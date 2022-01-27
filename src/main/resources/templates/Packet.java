public interface Packet {
    void fromNMS(Object raw, int ver);
    Object toNMS(int ver);
    void read(Object buf, int ver);
    void write(Object buf, int ver);
}