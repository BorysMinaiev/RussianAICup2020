import java.util.ArrayList;
import java.util.List;

public class CachedArrays {
    static class IntArray {
        private int[] values;
        private int[] valuesVersion;
        private int currentVersion;
        private boolean inUse;

        IntArray(int size) {
            values = new int[size];
            valuesVersion = new int[size];
            currentVersion = 1;
            inUse = false;
        }

        public int getSize() {
            return values.length;
        }

        public void startUsing() {
            currentVersion++;
            inUse = true;
        }

        public void stopUsing() {
            inUse = false;
        }

        public int get(int pos) {
            if (valuesVersion[pos] == currentVersion) {
                return values[pos];
            }
            throw new AssertionError("Element " + pos + " wasn't set before.");
        }

        public boolean contains(int pos) {
            return valuesVersion[pos] == currentVersion;
        }

        public int getOrDefault(int pos, int defaultValue) {
            if (valuesVersion[pos] == currentVersion) {
                return values[pos];
            }
            return defaultValue;
        }

        public void put(int pos, int value) {
            if (value == NOT_A_VALUE) {
                throw new AssertionError("Setting " + NOT_A_VALUE + " is not supported");
            }
            valuesVersion[pos] = currentVersion;
            values[pos] = value;
        }
    }


    private static List<IntArray> arrays = new ArrayList<>();

    private static IntArray findExistingArray(int size) {
        for (IntArray intArray : arrays) {
            if (intArray.inUse) {
                continue;
            }
            if (intArray.getSize() == size) {
                intArray.startUsing();
                return intArray;
            }
        }
        return null;
    }

    public static IntArray getNewIntArray(int size) {
        IntArray res = findExistingArray(size);
        if (res == null) {
            res = new IntArray(size);
            arrays.add(res);
            res.startUsing();
        }
        return res;
    }

    public static void resetAllArrays() {
        for (IntArray intArray : arrays) {
            intArray.stopUsing();
        }
    }


    public static final int NOT_A_VALUE = Integer.MIN_VALUE;
}
