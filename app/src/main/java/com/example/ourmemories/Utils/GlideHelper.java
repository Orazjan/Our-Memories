package com.example.ourmemories.Utils;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.util.Log;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.swiperefreshlayout.widget.CircularProgressDrawable;

import com.bumptech.glide.Glide;
import com.bumptech.glide.Priority;
import com.bumptech.glide.load.DataSource;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.bumptech.glide.load.engine.GlideException;
import com.bumptech.glide.load.resource.bitmap.CenterCrop;
import com.bumptech.glide.load.resource.bitmap.RoundedCorners;
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions;
import com.bumptech.glide.request.RequestListener;
import com.bumptech.glide.request.RequestOptions;
import com.bumptech.glide.request.target.Target;

public class GlideHelper {
    /**
     * Загрузка аватара по URL (строка).
     * Используется для загрузки из Firebase.
     */
    public static void loadAvatar(ImageView imageView, String url, String debugTag) {
        imageView.clearColorFilter();
        imageView.setImageTintList(null);

        if (url != null && !url.isEmpty()) {
            imageView.setPadding(0, 0, 0, 0);

            RequestOptions requestOptions = new RequestOptions().timeout(60000).diskCacheStrategy(DiskCacheStrategy.ALL).placeholder(android.R.drawable.ic_menu_camera).error(android.R.drawable.stat_notify_error).circleCrop().priority(Priority.HIGH)
                    .override(300, 300);

            Glide.with(imageView.getContext()).load(url).apply(requestOptions).thumbnail(0.1f).transition(DrawableTransitionOptions.withCrossFade())
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

    /**
     * === ДОБАВЛЕНО ===
     * Загрузка аватара по URI (локальный файл).
     * Нужно для EditProfileFragment при выборе фото из галереи.
     */
    public static void loadAvatar(ImageView imageView, Uri uri) {
        imageView.clearColorFilter();
        imageView.setImageTintList(null);

        if (uri != null) {
            imageView.setPadding(0, 0, 0, 0);

            RequestOptions requestOptions = new RequestOptions().diskCacheStrategy(DiskCacheStrategy.ALL).circleCrop().override(300, 300);

            Glide.with(imageView.getContext()).load(uri).apply(requestOptions).transition(DrawableTransitionOptions.withCrossFade()).into(imageView);
        }
    }
    /**
     * Загрузка галереи.
     */
    public static void loadGalleryImage(ImageView imageView, String url) {
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

            Glide.with(imageView.getContext())
                    .load(url)
                    .apply(requestOptions).thumbnail(0.05f).transition(DrawableTransitionOptions.withCrossFade())
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

    /**
     * Загрузка изображения для виджета.
     * @param context
     * @param url
     * @param target
     */
    public static void loadWidgetImage(Context context, String url, Target<Bitmap> target) {
        if (url == null || url.isEmpty()) return;

        RequestOptions requestOptions = new RequestOptions().timeout(60000).diskCacheStrategy(DiskCacheStrategy.ALL).override(300, 300)
                .transform(new CenterCrop(), new RoundedCorners(40));

        Glide.with(context.getApplicationContext()).asBitmap().load(url).apply(requestOptions).into(target);
    }
}