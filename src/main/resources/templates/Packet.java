/*
 * This file is part of tinyprotocol2, licensed under the MIT License.
 *
 * Copyright (c) 2022 Matouš Kučera
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */

package {utilsPackage};

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