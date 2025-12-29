package com.example.ourmemories.Utils;

import android.graphics.drawable.Drawable;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

public class GlideHelper {

    // Для аватарок (круглые)
    public static void loadAvatar(ImageView imageView, String url, String debugTag) {
        imageView.clearColorFilter();
        imageView.setImageTintList(null);

        if (url != null && !url.isEmpty()) {
            imageView.setPadding(0, 0, 0, 0);

            RequestOptions requestOptions = new RequestOptions()
                    .timeout(60000) // 60 секунд
                    .diskCacheStrategy(DiskCacheStrategy.ALL) // Кэшируем всё
                    .placeholder(android.R.drawable.ic_menu_camera)
                    .error(android.R.drawable.stat_notify_error)
                    .circleCrop()
                    .priority(Priority.HIGH) // Высокий приоритет для аватарок
                    .override(300, 300); // <--- ОПТИМИЗАЦИЯ: Ограничиваем размер декодирования

            Glide.with(imageView.getContext())
                    .load(url)
                    .apply(requestOptions)
                    .thumbnail(0.1f) // Показываем 10% качества пока грузится
                    .transition(DrawableTransitionOptions.withCrossFade()) // Плавный переход
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e("GlideHelper", "Avatar Error [" + debugTag + "]: " + (e != null ? e.getMessage() : "Unknown"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_camera);
            imageView.setPadding(20, 20, 20, 20);
        }
    }

    public static void loadGalleryImage(ImageView imageView, String url) {
        // Спиннер загрузки
        CircularProgressDrawable circularProgress = new CircularProgressDrawable(imageView.getContext());
        circularProgress.setStrokeWidth(5f);
        circularProgress.setCenterRadius(30f);
        circularProgress.start();

        if (url != null && !url.isEmpty()) {
            RequestOptions requestOptions = new RequestOptions()
                    .timeout(60000)
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(circularProgress)
                    .error(android.R.drawable.stat_notify_error);
            // .override() здесь не ставим жестко, пусть Glide сам решает на основе ImageView

            Glide.with(imageView.getContext())
                    .load(url)
                    .apply(requestOptions)
                    .thumbnail(0.05f) // <--- ОПТИМИЗАЦИЯ: Грузим 5% версию мгновенно (для медленного интернета)
                    .transition(DrawableTransitionOptions.withCrossFade()) // Плавное появление
                    .listener(new RequestListener<Drawable>() {
                        @Override
                        public boolean onLoadFailed(@Nullable GlideException e, Object model, Target<Drawable> target, boolean isFirstResource) {
                            Log.e("GlideHelper", "Gallery Error: " + (e != null ? e.getMessage() : "Unknown"));
                            return false;
                        }

                        @Override
                        public boolean onResourceReady(Drawable resource, Object model, Target<Drawable> target, DataSource dataSource, boolean isFirstResource) {
                            return false;
                        }
                    })
                    .into(imageView);
        } else {
            imageView.setImageResource(android.R.drawable.ic_menu_gallery);
        }
    }
}
