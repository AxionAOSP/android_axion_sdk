package com.android.server.pm;

public interface IAxDexoptManager {
    IAxDexoptManager DEFAULT = new IAxDexoptManager() {
        @Override
        public void notifyPackageUse(String packageName, int reason) {
        }

        @Override
        public void disableCompensateDexoptByCmd(boolean disable) {
        }
    };

    void notifyPackageUse(String packageName, int reason);
    void disableCompensateDexoptByCmd(boolean disable);
}
