package com.example.bookmyticket.features.tickets;

import com.example.bookmyticket.R;
import com.example.bookmyticket.model.Ticket;

import android.graphics.Color;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

public class TicketAdapter extends RecyclerView.Adapter<TicketAdapter.TicketViewHolder> {

    private static final String TAG = "TicketAdapter";
    private List<Ticket> ticketList;
    private final OnTicketClickListener listener;

    public interface OnTicketClickListener {
        void onTicketClick(Ticket ticket);
    }

    public TicketAdapter(List<Ticket> ticketList, OnTicketClickListener listener) {
        this.ticketList = new ArrayList<>(ticketList);
        this.listener = listener;
        Log.d(TAG, "Adapter created with " + ticketList.size() + " tickets");
    }

    @NonNull
    @Override
    public TicketViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_ticket, parent, false);
        return new TicketViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull TicketViewHolder holder, int position) {
        Ticket ticket = ticketList.get(position);
        Log.d(TAG, "Binding ticket at position " + position + ": " + ticket.getTicketId());
        holder.bind(ticket, listener);
    }

    @Override
    public int getItemCount() {
        return ticketList.size();
    }

    public void updateTickets(List<Ticket> newTickets) {
        this.ticketList.clear();
        this.ticketList.addAll(newTickets);
        Log.d(TAG, "Updated tickets list with " + newTickets.size() + " items");
        notifyDataSetChanged();
    }

    static class TicketViewHolder extends RecyclerView.ViewHolder {
        private final TextView tvTicketId;
        private final TextView tvAmount;
        private final TextView tvDate;
        private final TextView tvStatus;
        private final TextView tvPersons;

        public TicketViewHolder(@NonNull View itemView) {
            super(itemView);
            tvTicketId = itemView.findViewById(R.id.tvTicketId);
            tvAmount = itemView.findViewById(R.id.tvAmount);
            tvDate = itemView.findViewById(R.id.tvDate);
            tvStatus = itemView.findViewById(R.id.tvStatus);
            tvPersons = itemView.findViewById(R.id.tvPersons);
        }

        public void bind(final Ticket ticket, final OnTicketClickListener listener) {
            if (ticket == null) {
                Log.e(TAG, "Null ticket in bind");
                return;
            }

            // Set ticket data
            tvTicketId.setText(ticket.getTicketId());
            tvAmount.setText(ticket.getFormattedAmount());
            tvDate.setText(ticket.getFormattedDate());
            tvPersons.setText(String.format("%d persons", ticket.getTotalPersons()));

            // Handle status display
            String status = ticket.getStatus().toLowerCase();
            tvStatus.setText(ticket.getStatus().toUpperCase());
            tvStatus.setTextColor(getStatusColor(status));

            itemView.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onTicketClick(ticket);
                }
            });
        }

        private int getStatusColor(String status) {
            switch (status) {
                case "completed":
                    return Color.parseColor("#4CAF50"); // Green
                case "processing":
                    return Color.parseColor("#FFC107"); // Amber
                case "pending":
                    return Color.parseColor("#F44336"); // Red
                default:
                    return Color.parseColor("#9E9E9E"); // Grey
            }
        }
    }
}
