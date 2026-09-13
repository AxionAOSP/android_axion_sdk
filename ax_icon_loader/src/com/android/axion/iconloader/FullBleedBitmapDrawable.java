package com.android.axion.iconloader;

import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

public class FullBleedBitmapDrawable extends BitmapDrawable {

    private final String mIconPackPackage;

    public FullBleedBitmapDrawable(@NonNull Resources res, @NonNull Bitmap bitmap) {
        this(res, bitmap, null);
    }

    public FullBleedBitmapDrawable(@NonNull Resources res, @NonNull Bitmap bitmap,
            @Nullable String iconPackPackage) {
        super(res, bitmap);
        mIconPackPackage = iconPackPackage;
    }

    @Nullable
    public String getIconPackPackage() {
        return mIconPackPackage;
    }
}
