package com.onvifscanner.ui;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.onvifscanner.CameraDevice;
import com.onvifscanner.R;

import java.util.List;
import java.util.function.BiConsumer;

public class CameraAdapter extends RecyclerView.Adapter<CameraAdapter.ViewHolder> {

    private final List<CameraDevice> cameras;
    private final BiConsumer<CameraDevice, Integer> onClick;
    private final BiConsumer<CameraDevice, Integer> onLongClick;

    public CameraAdapter(List<CameraDevice> cameras, 
                         BiConsumer<CameraDevice, Integer> onClick,
                         BiConsumer<CameraDevice, Integer> onLongClick) {
        this.cameras = cameras;
        this.onClick = onClick;
        this.onLongClick = onLongClick;
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
        CameraDevice camera = cameras.get(position);
        
        holder.txtName.setText(camera.getName());
        holder.txtIp.setText(camera.getIpAddress() + ":" + camera.getPort());
        
        String manufacturer = camera.getManufacturer();
        String model = camera.getModel();
        if (manufacturer != null || model != null) {
            String info = (manufacturer != null ? manufacturer : "") + 
                         (model != null ? " " + model : "");
            holder.txtInfo.setText(info.trim());
            holder.txtInfo.setVisibility(View.VISIBLE);
        } else {
            holder.txtInfo.setVisibility(View.GONE);
        }

        holder.itemView.setOnClickListener(v -> onClick.accept(camera, position));
        holder.itemView.setOnLongClickListener(v -> {
            onLongClick.accept(camera, position);
            return true;
        });
    }

    @Override
    public int getItemCount() {
        return cameras.size();
    }

    static class ViewHolder extends RecyclerView.ViewHolder {
        ImageView imgIcon;
        TextView txtName;
        TextView txtIp;
        TextView txtInfo;

        ViewHolder(View itemView) {
            super(itemView);
            imgIcon = itemView.findViewById(R.id.imgIcon);
            txtName = itemView.findViewById(R.id.txtName);
            txtIp = itemView.findViewById(R.id.txtIp);
            txtInfo = itemView.findViewById(R.id.txtInfo);
        }
    }
}
