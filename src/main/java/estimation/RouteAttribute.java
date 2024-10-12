package estimation;

import org.matsim.api.core.v01.network.Link;

import java.util.function.Predicate;
import java.util.function.ToDoubleFunction;

public class RouteAttribute {

    final String name;
    final String baseAttrName;
    final ToDoubleFunction<Link> value;
    final Predicate<Integer> filter;

    public RouteAttribute(String name, ToDoubleFunction<Link> value) {
        this.name = name;
        this.baseAttrName = null;
        this.value = value;
        this.filter = i -> true;
    }

    public RouteAttribute(String name, String baseAttrName, Predicate<Integer> filter) {
        this.name = name;
        this.baseAttrName = baseAttrName;
        this.value = null;
        this.filter = filter;
    }

    public String getName() {
        return name;
    }

    public String getCorrespondingBaseAttributeName() {
        return baseAttrName;
    }

    public boolean isBaseAttribute() {
        return baseAttrName == null;
    }

    public double getValue(Link link) {
        return value.applyAsDouble(link);
    }

    public boolean test(Integer i) {
        return filter.test(i);
    }

}
