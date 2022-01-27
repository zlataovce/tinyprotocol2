public interface Packet {
    Object toNMS(int ver);
    void write(Object buf, int ver);
}