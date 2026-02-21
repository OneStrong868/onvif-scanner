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

import java.util.List;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.ViewHolder> {

    private List<OnvifCamera> cameras;
    private OnCameraClickListener listener;

    public interface OnCameraClickListener {
        void onCameraClick(OnvifCamera camera);
        void onCameraDelete(OnvifCamera camera);
    }

    public CameraAdapter(List<OnvifCamera> cameras, OnCameraClickListener listener) {
        this.cameras = cameras;
        this.listener = listener;
    }

    public void updateCameras(List<OnvifCamera> cameras) {
        this.cameras = cameras;
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
        
        holder.tvName.setText(camera.getName());
        holder.tvIp.setText(camera.getIpAddress() != null ? camera.getIpAddress() : "Manual");
        holder.tvRtsp.setText(camera.getRtspUrl());
        
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
        return cameras.size();
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
