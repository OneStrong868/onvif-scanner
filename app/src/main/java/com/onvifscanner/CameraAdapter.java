package com.onvifscanner;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.onvifscanner.camera.OnvifCamera;

import java.util.ArrayList;
import java.util.List;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.ViewHolder> {

    private List<OnvifCamera> cameras = new ArrayList<>();
    private OnCameraClickListener listener;

    public interface OnCameraClickListener {
        void onCameraClick(OnvifCamera camera);
        void onCameraDelete(OnvifCamera camera);
    }

    public CameraAdapter(List<OnvifCamera> cameras, OnCameraClickListener listener) {
        this.cameras = cameras != null ? cameras : new ArrayList<>();
        this.listener = listener;
    }

    public void updateCameras(List<OnvifCamera> newCameras) {
        this.cameras = newCameras != null ? new ArrayList<>(newCameras) : new ArrayList<>();
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
            .inflate(R.layout.item_camera, parent, false);
        return new ViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        OnvifCamera camera = cameras.get(position);
        
        holder.tvName.setText(camera.getName() != null ? camera.getName() : "Unknown Camera");
        holder.tvIp.setText(camera.getIpAddress() != null ? camera.getIpAddress() : "Manual Entry");
        holder.tvRtsp.setText(camera.getRtspUrl() != null ? camera.getRtspUrl() : "");
        
        holder.ivType.setImageResource(
            camera.isManual() ? R.drawable.ic_camera_manual : R.drawable.ic_camera_onvif
        );

        holder.itemView.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCameraClick(camera);
            }
        });

        holder.btnDelete.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCameraDelete(camera);
            }
        });
    }

    @Override
    public int getItemCount() {
        return cameras != null ? cameras.size() : 0;
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView ivType;
        TextView tvName, tvIp, tvRtsp;
        ImageButton btnDelete;

        ViewHolder(View itemView) {
            super(itemView);
            ivType = itemView.findViewById(R.id.ivCameraType);
            tvName = itemView.findViewById(R.id.tvCameraName);
            tvIp = itemView.findViewById(R.id.tvCameraIp);
            tvRtsp = itemView.findViewById(R.id.tvRtspUrl);
            btnDelete = itemView.findViewById(R.id.btnDelete);
        }
    }
}
