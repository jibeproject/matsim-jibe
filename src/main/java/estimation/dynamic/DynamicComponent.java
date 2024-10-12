package estimation.dynamic;

public interface DynamicComponent {

    void update(double[] x);

    String getStats();

}
