package com.example.ourmemories.Utils;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

public class GlideHelper {

    public static void loadAvatar(ImageView imageView, String url, String debugTag) {
        imageView.clearColorFilter();
        imageView.setImageTintList(null);

        if (url != null && !url.isEmpty()) {
            imageView.setPadding(0, 0, 0, 0);

            RequestOptions requestOptions = new RequestOptions()
                    .timeout(60000)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(android.R.drawable.ic_menu_camera)
                    .error(android.R.drawable.stat_notify_error)
                    .circleCrop();

            Glide.with(imageView.getContext())
                    .load(url)
                    .apply(requestOptions)
                    .thumbnail(0.1f)
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e("GlideHelper", "Ошибка [" + debugTag + "]: " + (e != null ? e.getMessage() : "Unknown"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            Log.d("GlideHelper", "Успех [" + debugTag + "]");
                            return false;
                        }
                    })
                    .into(imageView);
        } else {
            // Если фото нет, возвращаем иконку
            imageView.setImageResource(android.R.drawable.ic_menu_camera);
            imageView.setPadding(20, 20, 20, 20);
        }
    }
}