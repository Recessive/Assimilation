package main;

public class Tuple<type1, type2> {

    private type1 a;
    private type2 b;

    /**
     * Constructor - simply sets field variables for each value
     *
     * @param a
     * @param b
     */
    public Tuple(type1 a, type2 b){
        this.a = a;
        this.b = b;
    }

    /**
     * Returns either value stored within tuple
     *
     * @param index
     * @return Object
     */
    public Object get(int index){
        assert index == 0 || index == 1;
        if(index == 0){
            return a;
        }else{
            return b;
        }
    }

    /**
     * Returns true if both a and b are equal to this tuples a and b
     *
     * @param other
     * @return
     */
    @Override
    public boolean equals(Object other) {
        return (((Tuple) other).a.equals(this.a) && ((Tuple) other).b.equals(this.b));
    }

    @Override
    public int hashCode() {
        int result = (int) (18 ^ (18 >>> 32));
        result = 31 * result + a.hashCode();
        result = 31 * result + b.hashCode();
        return result;
    }

    @Override
    public String toString() {
        return "(" + this.a + ", " + this.b + ")";
    }
}

