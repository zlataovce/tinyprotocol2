/**
 * A base for a packet wrapper.
 */
public interface Packet {
    /**
     * Populates this packet wrapper instance with values from the supplied NMS packet.
     *
     * @param raw the raw (net.minecraft) packet
     * @param ver the current <strong>server</strong> protocol version
     */
    void fromNMS(Object raw, int ver);

    /**
     * Creates a new NMS packet and immediately populates it with values from this packet wrapper instance.
     *
     * @param ver the current <strong>server</strong> protocol version
     * @return the raw (net.minecraft) packet
     */
    Object toNMS(int ver);

    /**
     * Populates this packet wrapper instance with the contents of the supplied
     * <a href="https://nms.screamingsandals.org/1.18.1/net/minecraft/network/FriendlyByteBuf.html">net.minecraft.network.FriendlyByteBuf</a> instance.
     * <p>
     * <strong>Note:</strong><br>
     * This method creates a new NMS packet as an intermediate conversion stage, if you already have the NMS packet, consider using the {@link #fromNMS(Object, int)} method.
     *
     * @param buf the FriendlyByteBuf buffer
     * @param ver the current <strong>server</strong> protocol version
     */
    void read(Object buf, int ver);

    /**
     * Populates the supplied <a href="https://nms.screamingsandals.org/1.18.1/net/minecraft/network/FriendlyByteBuf.html">net.minecraft.network.FriendlyByteBuf</a> instance
     * with values of this packet wrapper instance.
     * <p>
     * <strong>Note:</strong><br>
     * This method creates a new NMS packet as an intermediate conversion stage.
     *
     * @param buf the FriendlyByteBuf buffer
     * @param ver the current <strong>server</strong> protocol version
     */
    void write(Object buf, int ver);
}