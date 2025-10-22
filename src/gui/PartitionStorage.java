package gui;

import java.util.ArrayList;
import java.util.List;

public class PartitionStorage {
    private static List<String> partitions = new ArrayList<>();

    static {
        // sample partitions
        partitions.add("C:\\");
        partitions.add("D:\\");
    }

    public static List<String> getPartitions() {
        return partitions;
    }

    public static void addPartition(String name) {
        partitions.add(name);
    }

    public static void removePartition(String name) {
        partitions.remove(name);
    }
}
