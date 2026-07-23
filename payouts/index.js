const functions = require("firebase-functions");
const admin = require("firebase-admin");
const PDFDocument = require("pdfkit");
const nodemailer = require("nodemailer");
const { Payouts } = require("@cashfreepayments/cashfree-sdk");
const { Buffer } = require("buffer");

// Initialize Firebase Admin
admin.initializeApp();

// Initialize Cashfree Payouts
const payoutsInstance = new Payouts({
  env: "TEST", // Use "PRODUCTION" for live
  clientId: functions.config().cashfree.client_id,
  clientSecret: functions.config().cashfree.client_secret,
  publicKey: functions.config().cashfree.public_key,
});

// Initialize Nodemailer
const transporter = nodemailer.createTransport({
  service: "gmail",
  auth: {
    user: functions.config().email.user,
    pass: functions.config().email.pass,
  },
});

// Demo fixed details
const FIXED_BANK_ACCOUNT = "1234567890"; // Replace with actual bank account
const FIXED_IFSC = "HDFC0000001"; // Replace with actual IFSC code
const FIXED_EMAIL = "demo@example.com"; // Replace with actual email
const FIXED_ADMIN_NAME = "Demo Admin"; // Replace with actual admin name
const DEMO_USER_ID = "624E5L"; // Demo userId
const DEMO_BENE_ID = "DEMO_BENE_624E5L"; // Unique beneficiary ID

// Helper: Generate PDF
function generatePDF(adminName, tickets, totalPayout) {
  return new Promise((resolve, reject) => {
    try {
      const doc = new PDFDocument();
      const buffers = [];
      doc.on("data", buffers.push.bind(buffers));
      doc.on("end", () => resolve(Buffer.concat(buffers)));
      doc.on("error", (err) => reject(err));

      doc.fontSize(16).text(`Payout Report for ${adminName}`, { align: "center" });
      doc.moveDown();
      doc.fontSize(12).text(`Date: ${new Date().toISOString().split("T")[0]}`);
      doc.moveDown();
      doc.text(`Total Payout: ₹${totalPayout}`);
      doc.moveDown();
      doc.text("Ticket Breakdown:");
      tickets.forEach((ticket) => {
        doc.text(`- Ticket ID: ${ticket.ticketId}, Amount: ₹${ticket.baseAmount}, Persons: ${ticket.totalPersons}`);
      });
      doc.end();
    } catch (err) {
      reject(err);
    }
  });
}

// HTTPS Callable Function for Demo
exports.demoPayout = functions
  .region("asia-south1")
  .https.onRequest(async (req, res) => {
    const db = admin.firestore();

    try {
      // Fetch three payout documents for demo user
      const ticketsSnapshot = await db
        .collection("payouts")
        .where("userId", "==", DEMO_USER_ID)
        .where("status", "in", ["", "success"])
        .limit(3) // Limit to three documents
        .get();

      if (ticketsSnapshot.empty) {
        return res.status(400).send("No tickets found for user.");
      }

      const tickets = ticketsSnapshot.docs.map((doc) => ({
        id: doc.id,
        ...doc.data(),
      }));
      const totalPayout = tickets.reduce((sum, ticket) => sum + ticket.baseAmount, 0);

      if (totalPayout === 0) {
        return res.status(400).send("Total payout is zero.");
      }

      // Add beneficiary (if not already added)
      try {
        await payoutsInstance.beneficiary.add({
          beneId: DEMO_BENE_ID,
          name: FIXED_ADMIN_NAME,
          email: FIXED_EMAIL,
          phone: "9876543210", // Replace with actual phone
          bankAccount: FIXED_BANK_ACCOUNT,
          ifsc: FIXED_IFSC,
          address1: "Demo Address",
          city: "Bangalore",
          state: "Karnataka",
          pincode: "560001",
        });
      } catch (error) {
        if (error.message.includes("Beneficiary already exists")) {
          console.log("Beneficiary already exists, proceeding with payout.");
        } else {
          throw error;
        }
      }

      // Trigger Cashfree payout
      const payoutData = {
        beneId: DEMO_BENE_ID,
        amount: totalPayout.toFixed(2), // Cashfree expects amount as string with 2 decimals
        transferId: `payout_${DEMO_USER_ID}_${Date.now()}`,
        transferMode: "imps",
        remarks: `Demo Payout for ${FIXED_ADMIN_NAME}`,
      };

      const payoutResponse = await payoutsInstance.requestTransfer(payoutData);

      // Log payout
      await db.collection("payout_logs").add({
        userId: DEMO_USER_ID,
        amount: totalPayout,
        date: new Date(),
        status: "success",
        transferId: payoutResponse.data.transferId,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      // Generate PDF
      const pdfBuffer = await generatePDF(FIXED_ADMIN_NAME, tickets, totalPayout);

      // Send email
      await transporter.sendMail({
        from: '"Quick Entry Team" <your-email@gmail.com>',
        to: FIXED_EMAIL,
        subject: `Demo Payout from Quick Entry – Credited`,
        text: `
          Hello ${FIXED_ADMIN_NAME},

          Your demo payout has been successfully credited to your registered bank account ending in ****${FIXED_BANK_ACCOUNT.slice(-4)}.

          💰 Total Payout: ₹${totalPayout}
          🏦 Ticket breakdown is attached in the report.

          Thank you for using Quick Entry!

          Regards,
          Quick Entry Team
        `,
        attachments: [
          {
            filename: `Demo_Payout_${FIXED_ADMIN_NAME}_${new Date().toISOString().split("T")[0]}.pdf`,
            content: pdfBuffer,
            contentType: "application/pdf",
          },
        ],
      });

      return res.status(200).send(`Payout processed and email sent for user ${DEMO_USER_ID}. Total: ₹${totalPayout}`);
    } catch (error) {
      console.error(`Payout failed for user ${DEMO_USER_ID}:`, error);

      // Log failed payout
      await db.collection("payout_logs").add({
        userId: DEMO_USER_ID,
        amount: totalPayout || 0,
        date: new Date(),
        status: "failed",
        error: error.message,
        createdAt: admin.firestore.FieldValue.serverTimestamp(),
      });

      return res.status(500).send(`Error: ${error.message}`);
    }
  });