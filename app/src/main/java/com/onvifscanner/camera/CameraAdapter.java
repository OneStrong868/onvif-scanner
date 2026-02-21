package com.onvifscanner.camera;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.DiffUtil;
import androidx.recyclerview.widget.ListAdapter;
import androidx.recyclerview.widget.RecyclerView;

import com.onvifscanner.R;

public class CameraAdapter extends ListAdapter<Camera, CameraAdapter.ViewHolder> {

    public interface OnCameraClickListener {
        void onCameraClick(Camera camera);
        void onCameraDelete(Camera camera);
        void onCameraEdit(Camera camera);
    }

    private OnCameraClickListener listener;

    public CameraAdapter(OnCameraClickListener listener) {
        super(DIFF_CALLBACK);
        this.listener = listener;
    }

    private static final DiffUtil.ItemCallback<Camera> DIFF_CALLBACK = 
        new DiffUtil.ItemCallback<Camera>() {
            @Override
            public boolean areItemsTheSame(@NonNull Camera oldItem, @NonNull Camera newItem) {
                return oldItem.getId().equals(newItem.getId());
            }

            @Override
            public boolean areContentsTheSame(@NonNull Camera oldItem, @NonNull Camera newItem) {
                return oldItem.getIpAddress().equals(newItem.getIpAddress()) &&
                       oldItem.getPort() == newItem.getPort() &&
                       oldItem.getName().equals(newItem.getName());
            }
        };

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_camera, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        Camera camera = getItem(position);
        holder.bind(camera, listener);
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        private final TextView textName;
        private final TextView textIp;
        private final TextView textStatus;
        private final ImageView iconType;
        private final ImageButton btnDelete;
        private final ImageButton btnEdit;

        public ViewHolder(@NonNull View itemView) {
            super(itemView);
            textName = itemView.findViewById(R.id.textCameraName);
            textIp = itemView.findViewById(R.id.textCameraIp);
            textStatus = itemView.findViewById(R.id.textCameraStatus);
            iconType = itemView.findViewById(R.id.iconCameraType);
            btnDelete = itemView.findViewById(R.id.btnDelete);
            btnEdit = itemView.findViewById(R.id.btnEdit);
        }

        public void bind(Camera camera, OnCameraClickListener listener) {
            textName.setText(camera.getDisplayName());
            textIp.setText(camera.getIpAddress() + ":" + camera.getPort());
            textStatus.setText(camera.isOnline() ? "Online" : "Offline");
            textStatus.setTextColor(itemView.getContext().getColor(
                camera.isOnline() ? R.color.online : R.color.offline));
            
            iconType.setImageResource(camera.isManual() ? 
                R.drawable.ic_manual : R.drawable.ic_discovered);

            itemView.setOnClickListener(v -> {
                if (listener != null) listener.onCameraClick(camera);
            });

            btnDelete.setOnClickListener(v -> {
                if (listener != null) listener.onCameraDelete(camera);
            });

            btnEdit.setOnClickListener(v -> {
                if (listener != null) listener.onCameraEdit(camera);
            });
        }
    }
}
