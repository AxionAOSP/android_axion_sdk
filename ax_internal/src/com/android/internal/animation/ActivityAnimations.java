package com.android.internal.animation;

import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.Interpolator;
import android.view.animation.LinearInterpolator;
import android.view.animation.TranslateAnimation;

public final class ActivityAnimations {

    private static final float DAMPING_RATIO = 0.85f;
    private static final float STIFFNESS = 850f;
    private static final float DISTANCE = 0.333f;
    private static final long APP_STARTING_EXIT_DURATION_MS = 150L;

    private static SpringInterpolator sSpatialSpec;
    private static Animation sPreloadedOpenEnter;
    private static Animation sPreloadedOpenExit;
    private static Animation sPreloadedCloseEnter;
    private static Animation sPreloadedCloseExit;
    private static Animation sPreloadedTranslucentOpenEnter;
    private static Animation sPreloadedTranslucentCloseExit;
    private static Animation sPreloadedAppStartingExit;

    private ActivityAnimations() {}

    public static synchronized void preload() {
        if (sSpatialSpec == null) {
            sSpatialSpec = new SpringInterpolator(DAMPING_RATIO, STIFFNESS);
        }
        if (sPreloadedOpenEnter == null) {
            sPreloadedOpenEnter = createSlideAnimation(1.0f, 0.0f, Animation.ZORDER_TOP);
        }
        if (sPreloadedOpenExit == null) {
            sPreloadedOpenExit = createSlideAnimation(0.0f, -DISTANCE, Animation.ZORDER_NORMAL);
        }
        if (sPreloadedCloseEnter == null) {
            sPreloadedCloseEnter = createSlideAnimation(-DISTANCE, 0.0f, Animation.ZORDER_NORMAL);
        }
        if (sPreloadedCloseExit == null) {
            sPreloadedCloseExit = createSlideAnimation(0.0f, 1.0f, Animation.ZORDER_TOP);
        }
        if (sPreloadedTranslucentOpenEnter == null) {
            sPreloadedTranslucentOpenEnter = createVerticalSlideAnimation(1.0f, 0.0f, Animation.ZORDER_TOP);
        }
        if (sPreloadedTranslucentCloseExit == null) {
            sPreloadedTranslucentCloseExit = createVerticalSlideAnimation(0.0f, 1.0f, Animation.ZORDER_TOP);
        }
        if (sPreloadedAppStartingExit == null) {
            sPreloadedAppStartingExit = buildAppStartingExit();
        }
    }

    private static Animation buildAppStartingExit() {
        AlphaAnimation animation = new AlphaAnimation(1.0f, 0.0f);
        animation.setDuration(APP_STARTING_EXIT_DURATION_MS);
        animation.setInterpolator(new LinearInterpolator());
        animation.setFillEnabled(true);
        animation.setFillBefore(true);
        animation.setFillAfter(true);
        animation.setZAdjustment(Animation.ZORDER_TOP);
        return animation;
    }

    private static Animation createSlideAnimation(float fromX, float toX, int zAdjustment) {
        if (sSpatialSpec == null) {
            preload();
        }
        TranslateAnimation slide = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, fromX,
                Animation.RELATIVE_TO_SELF, toX,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f
        );
        slide.setDuration(sSpatialSpec.getDurationMs());
        slide.setInterpolator(sSpatialSpec);
        slide.setFillEnabled(true);
        slide.setFillBefore(true);
        slide.setFillAfter(true);
        slide.setZAdjustment(zAdjustment);
        slide.setHasRoundedCorners(true);
        return slide;
    }

    private static Animation createVerticalSlideAnimation(float fromY, float toY, int zAdjustment) {
        if (sSpatialSpec == null) {
            preload();
        }
        TranslateAnimation slide = new TranslateAnimation(
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, 0f,
                Animation.RELATIVE_TO_SELF, fromY,
                Animation.RELATIVE_TO_SELF, toY
        );
        slide.setDuration(sSpatialSpec.getDurationMs());
        slide.setInterpolator(sSpatialSpec);
        slide.setFillEnabled(true);
        slide.setFillBefore(true);
        slide.setFillAfter(true);
        slide.setZAdjustment(zAdjustment);
        slide.setHasRoundedCorners(true);
        return slide;
    }

    public static Animation getOpenEnter() {
        Animation preloaded = sPreloadedOpenEnter;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedOpenEnter = null;
            return preloaded;
        }
        return createSlideAnimation(1.0f, 0.0f, Animation.ZORDER_TOP);
    }

    public static Animation getOpenExit() {
        Animation preloaded = sPreloadedOpenExit;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedOpenExit = null;
            return preloaded;
        }
        return createSlideAnimation(0.0f, -DISTANCE, Animation.ZORDER_NORMAL);
    }

    public static Animation getCloseEnter() {
        Animation preloaded = sPreloadedCloseEnter;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedCloseEnter = null;
            return preloaded;
        }
        return createSlideAnimation(-DISTANCE, 0.0f, Animation.ZORDER_NORMAL);
    }

    public static Animation getCloseExit() {
        Animation preloaded = sPreloadedCloseExit;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedCloseExit = null;
            return preloaded;
        }
        return createSlideAnimation(0.0f, 1.0f, Animation.ZORDER_TOP);
    }

    public static Animation getTranslucentOpenEnter() {
        Animation preloaded = sPreloadedTranslucentOpenEnter;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedTranslucentOpenEnter = null;
            return preloaded;
        }
        return createVerticalSlideAnimation(1.0f, 0.0f, Animation.ZORDER_TOP);
    }

    public static Animation getTranslucentCloseExit() {
        Animation preloaded = sPreloadedTranslucentCloseExit;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedTranslucentCloseExit = null;
            return preloaded;
        }
        return createVerticalSlideAnimation(0.0f, 1.0f, Animation.ZORDER_TOP);
    }

    public static Animation getAppStartingExit() {
        Animation preloaded = sPreloadedAppStartingExit;
        if (preloaded != null && !preloaded.hasStarted()) {
            sPreloadedAppStartingExit = null;
            return preloaded;
        }
        return buildAppStartingExit();
    }

    public static final class SpringInterpolator implements Interpolator {
        private static final int LUT_SIZE = 256;

        private final float mDampingRatio;
        private final float mOmega0;
        private final long mDurationMs;
        private final float mDurationSec;
        private final float mEndOutput;
        private final float mEndGap;

        private final float mGamma;
        private final float mOmegaD;
        private final float mSinCoeff;
        private final float mR1;
        private final float mR2;
        private final float mInvR2MinusR1;

        private final float[] mLut = new float[LUT_SIZE + 1];

        public SpringInterpolator(float dampingRatio, float stiffness) {
            mDampingRatio = dampingRatio;
            mOmega0 = (float) Math.sqrt(stiffness);
            final float settleSec;
            if (dampingRatio >= 1.0f) {
                settleSec = 9.23f / mOmega0;
            } else {
                settleSec = 6.91f / (dampingRatio * mOmega0);
            }
            mDurationMs = Math.max(50L, (long) (settleSec * 1000f));
            mDurationSec = mDurationMs / 1000f;

            mGamma = dampingRatio * mOmega0;
            if (dampingRatio < 1.0f) {
                mOmegaD = mOmega0 * (float) Math.sqrt(1.0f - dampingRatio * dampingRatio);
                mSinCoeff = mGamma / mOmegaD;
                mR1 = 0f;
                mR2 = 0f;
                mInvR2MinusR1 = 0f;
            } else if (dampingRatio > 1.0f) {
                final float d = (float) Math.sqrt(dampingRatio * dampingRatio - 1.0f);
                mR1 = -mOmega0 * (dampingRatio - d);
                mR2 = -mOmega0 * (dampingRatio + d);
                mInvR2MinusR1 = 1.0f / (mR2 - mR1);
                mOmegaD = 0f;
                mSinCoeff = 0f;
            } else {
                mOmegaD = 0f;
                mSinCoeff = 0f;
                mR1 = 0f;
                mR2 = 0f;
                mInvR2MinusR1 = 0f;
            }

            mEndOutput = rawSpring(mDurationSec);
            mEndGap = 1.0f - mEndOutput;

            for (int i = 0; i <= LUT_SIZE; i++) {
                final float t = (float) i / LUT_SIZE;
                mLut[i] = rawSpring(t * mDurationSec) + mEndGap * t;
            }
            mLut[0] = 0.0f;
            mLut[LUT_SIZE] = 1.0f;
        }

        public long getDurationMs() {
            return mDurationMs;
        }

        private float rawSpring(float t) {
            if (mDampingRatio < 1.0f) {
                final float env = (float) Math.exp(-mGamma * t);
                return 1.0f - env * ((float) Math.cos(mOmegaD * t)
                        + mSinCoeff * (float) Math.sin(mOmegaD * t));
            } else if (mDampingRatio > 1.0f) {
                return 1.0f - (mR2 * (float) Math.exp(mR1 * t)
                        - mR1 * (float) Math.exp(mR2 * t)) * mInvR2MinusR1;
            } else {
                final float env = (float) Math.exp(-mGamma * t);
                return 1.0f - env * (1.0f + mOmega0 * t);
            }
        }

        @Override
        public float getInterpolation(float input) {
            if (input <= 0.0f) {
                return 0.0f;
            }
            if (input >= 1.0f) {
                return 1.0f;
            }
            final float position = input * LUT_SIZE;
            final int index = (int) position;
            final float fraction = position - index;
            return mLut[index] + fraction * (mLut[index + 1] - mLut[index]);
        }
    }
}
