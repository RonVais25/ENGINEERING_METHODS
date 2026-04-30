package common.model;

public class Park {
    private String name;
    private int maxCapacity;
    private int currentVisitors;

    public Park(String name, int maxCapacity) {
        this.name = name;
        this.maxCapacity = maxCapacity;
        this.currentVisitors = 0;
    }

    public String getName() {
        return name;
    }

    public int getMaxCapacity() {
        return maxCapacity;
    }

    public int getCurrentVisitors() {
        return currentVisitors;
    }

    public void addVisitors(int count) {
        this.currentVisitors += count;
    }

    public void removeVisitors(int count) {
        this.currentVisitors -= count;
    }

    public boolean hasCapacity(int requested) {
        return (currentVisitors + requested) <= maxCapacity;
    }
}