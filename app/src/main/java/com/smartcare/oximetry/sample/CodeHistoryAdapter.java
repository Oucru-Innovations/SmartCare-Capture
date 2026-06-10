package com.smartcare.oximetry.sample;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

/**
 * RecyclerView Adapter hiển thị danh sách research code đã dùng gần đây trong PatientInfoPopup,
 * cho phép chọn lại nhanh (onCodeClick) hoặc xoá khỏi lịch sử (onCodeDelete).
 */
public class CodeHistoryAdapter extends RecyclerView.Adapter<CodeHistoryAdapter.CodeViewHolder> {
    
    private List<String> codeList;
    private OnCodeClickListener listener;
    private OnCodeDeleteListener deleteListener;
    
    public interface OnCodeDeleteListener {
        void onCodeDelete(String code, int position);
    }
    
    public interface OnCodeClickListener {
        void onCodeClick(String code);
    }
    
    public CodeHistoryAdapter(List<String> codeList, OnCodeClickListener listener, OnCodeDeleteListener deleteListener) {
        this.codeList = codeList;
        this.listener = listener;
        this.deleteListener = deleteListener;
    }
    
    @NonNull
    @Override
    public CodeViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_code_history, parent, false);
        return new CodeViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull CodeViewHolder holder, int position) {
        String code = codeList.get(position);
        holder.codeText.setText(code);
        
        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCodeClick(code);
            }
        });
        
        // Setup delete button click
        holder.deleteButton.setOnClickListener(v -> {
            if (deleteListener != null) {
                deleteListener.onCodeDelete(code, holder.getAdapterPosition());
            }
        });
    }
    
    @Override
    public int getItemCount() {
        return codeList != null ? codeList.size() : 0;
    }
    
    /** Thay toàn bộ danh sách code và refresh list (dùng khi history được nạp lại). */
    public void updateCodes(List<String> newCodes) {
        this.codeList = newCodes;
        notifyDataSetChanged();
    }
    
    /** Xoá code tại {@code position} và animate xoá item để đồng bộ với lịch sử đã lưu. */
    public void removeCode(int position) {
        if (position >= 0 && position < codeList.size()) {
            codeList.remove(position);
            notifyItemRemoved(position);
        }
    }
    
    static class CodeViewHolder extends RecyclerView.ViewHolder {
        TextView codeText;
        ImageButton deleteButton;
        
        CodeViewHolder(View itemView) {
            super(itemView);
            codeText = itemView.findViewById(R.id.codeText);
            deleteButton = itemView.findViewById(R.id.deleteCodeButton);
        }
    }
}
