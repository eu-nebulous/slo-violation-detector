package utility_beans.synchronization;

public class SynchronizedDouble {
    private Double value;
    public SynchronizedDouble(Double value){
        this.value = value;
    }
    public SynchronizedDouble(){
        this(0.0);
    }

    public Double getValue() {
        return value;
    }

    public void setValue(Double value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
