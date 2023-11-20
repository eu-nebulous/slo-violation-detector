package utility_beans;

public class SynchronizedInteger {
    private Integer value;
    public SynchronizedInteger(Integer value){
        this.value = value;
    }
    public SynchronizedInteger(){
        this(0);
    }

    public Integer getValue() {
        return value;
    }

    public void setValue(Integer value) {
        this.value = value;
    }

    @Override
    public String toString() {
        return String.valueOf(value);
    }
}
