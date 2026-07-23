import http from "k6/http";
import {check, sleep} from "k6";

const baseUrl = __ENV.BASE_URL;
const destructiveAllowed = __ENV.ALLOW_DESTRUCTIVE_PAYOUT_TEST === "true";

if (!baseUrl || !destructiveAllowed) {
  throw new Error(
      "Refusing to run: set BASE_URL and ALLOW_DESTRUCTIVE_PAYOUT_TEST=true " +
      "for an isolated sandbox only.",
  );
}

export const options = {
  vus: 1,
  iterations: 1,
  thresholds: {
    http_req_failed: ["rate==0"],
    http_req_duration: ["p(95)<5000"],
  },
};

export default function() {
  const response = http.post(`${baseUrl}/demoPayout`, null, {
    tags: {operation: "destructive-demo-payout"},
  });

  check(response, {
    "returns a handled status": (result) =>
      [200, 400, 500].includes(result.status),
  });
  sleep(1);
}
