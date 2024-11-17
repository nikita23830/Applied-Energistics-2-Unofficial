package appeng.api.util;

public interface IVirtualItem {
    default long getDivision() {
        return 1;
    }
}
