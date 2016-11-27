/*
 * Copyright 2011 Google Inc.
 * Copyright 2015 Andreas Schildbach
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.inchain.core;


import java.io.IOException;
import java.io.OutputStream;
import java.math.BigInteger;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.UnknownHostException;
import java.util.Objects;

import org.inchain.core.exception.ProtocolException;
import org.inchain.message.Message;
import org.inchain.message.MessageSerializer;
import org.inchain.network.MainNetworkParams;
import org.inchain.network.NetworkParams;
import org.inchain.utils.Utils;

/**
 * <p>A PeerAddress holds an IP address and port number representing the network location of
 * a peer in the Bitcoin P2P network. It exists primarily for serialization purposes.</p>
 * 
 * <p>Instances of this class are not safe for use by multiple threads.</p>
 */
public class PeerAddress extends Message {

    static final int MESSAGE_SIZE = 30;

	private static final BigInteger DEFAULT_SERVICE = BigInteger.ONE;

    private InetAddress addr;
    private String hostname; // Used for .onion addresses
    private int port;
    private BigInteger services;
    private long time;

    /**
     * Construct a peer address from a serialized payload.
     */
    public PeerAddress(NetworkParams params, byte[] payload, int offset, int protocolVersion) throws ProtocolException {
        super(params, payload, offset, protocolVersion);
    }

    /**
     * Construct a peer address from a memorized or hardcoded address.
     */
    public PeerAddress(InetAddress addr, int port, int protocolVersion) {
        this.addr = Utils.checkNotNull(addr);
        this.port = port;
        this.protocolVersion = protocolVersion;
        this.services = DEFAULT_SERVICE;
        length = MESSAGE_SIZE;
    }

    /**
     * Constructs a peer address from the given IP address and port. Protocol version is the default
     * for Bitcoin.
     */
    public PeerAddress(InetAddress addr, int port) {
        this(addr, port, NetworkParams.ProtocolVersion.CURRENT.getVersion());
    }

    /**
     * Constructs a peer address from the given IP address and port.
     */
    public PeerAddress(NetworkParams params, InetAddress addr, int port) {
        this(addr, port, params.getProtocolVersionNum(NetworkParams.ProtocolVersion.CURRENT));
    }

    /**
     * Constructs a peer address from the given IP address. Port and version number
     * are default for Bitcoin mainnet.
     */
    public PeerAddress(InetAddress addr) {
        this(addr, MainNetworkParams.get().getPort());
    }

    /**
     * Constructs a peer address from the given IP address. Port is default for
     * Bitcoin mainnet, version number is default for the given parameters.
     */
    public PeerAddress(NetworkParams params, InetAddress addr) {
        this(params, addr, MainNetworkParams.get().getPort());
    }

    /**
     * Constructs a peer address from an {@link InetSocketAddress}. An InetSocketAddress can take in as parameters an
     * InetAddress or a String hostname. If you want to connect to a .onion, set the hostname to the .onion address.
     * Protocol version is the default.  Protocol version is the default
     * for Bitcoin.
     */
    public PeerAddress(InetSocketAddress addr) {
        this(addr.getAddress(), addr.getPort(), NetworkParams.ProtocolVersion.CURRENT.getVersion());
    }

    /**
     * Constructs a peer address from an {@link InetSocketAddress}. An InetSocketAddress can take in as parameters an
     * InetAddress or a String hostname. If you want to connect to a .onion, set the hostname to the .onion address.
     */
    public PeerAddress(NetworkParams params, InetSocketAddress addr) {
        this(params, addr.getAddress(), addr.getPort());
    }

    /**
     * Constructs a peer address from a stringified hostname+port. Use this if you want to connect to a Tor .onion address.
     * Protocol version is the default for Bitcoin.
     */
    public PeerAddress(String hostname, int port) {
        this.hostname = hostname;
        this.port = port;
        this.protocolVersion = NetworkParams.ProtocolVersion.CURRENT.getVersion();
        this.services = BigInteger.ZERO;
    }

    /**
     * Constructs a peer address from a stringified hostname+port. Use this if you want to connect to a Tor .onion address.
     */
    public PeerAddress(NetworkParams params, String hostname, int port) {
        super(params);
        this.hostname = hostname;
        this.port = port;
        this.protocolVersion = params.getProtocolVersionNum(NetworkParams.ProtocolVersion.CURRENT);
        this.services = BigInteger.ZERO;
    }

    public static PeerAddress localhost(NetworkParams params) {
        try {
			return new PeerAddress(params, InetAddress.getByName("127.0.0.1"), params.getPort());
		} catch (UnknownHostException e) {
			e.printStackTrace();
		}
        return null;
    }

    @Override
    protected void parse() throws ProtocolException {
        // Format of a serialized address:
        //   uint32 timestamp
        //   uint64 services   (flags determining what the node can do)
        //   16 bytes ip address
        //   2 bytes port num
        time = readUint32();
        services = readUint64();
        byte[] addrBytes = readBytes(16);
        try {
            addr = InetAddress.getByAddress(addrBytes);
        } catch (UnknownHostException e) {
            throw new RuntimeException(e);  // Cannot happen.
        }
        port = ((0xFF & payload[cursor++]) << 8) | (0xFF & payload[cursor++]);
        // The 4 byte difference is the uint32 timestamp that was introduced in version 31402 
        length = MESSAGE_SIZE;
    }

    @Override
	public void serializeToStream(OutputStream stream) throws IOException {
        int secs = (int) (Utils.currentTimeSeconds());
        Utils.uint32ToByteStreamLE(secs, stream);
        
        Utils.uint64ToByteStreamLE(services, stream);  // nServices.
        // Java does not provide any utility to map an IPv4 address into IPv6 space, so we have to do it by hand.
        byte[] ipBytes = addr.getAddress();
        if (ipBytes.length == 4) {
            byte[] v6addr = new byte[16];
            System.arraycopy(ipBytes, 0, v6addr, 12, 4);
            v6addr[10] = (byte) 0xFF;
            v6addr[11] = (byte) 0xFF;
            ipBytes = v6addr;
        }
        stream.write(ipBytes);
        // And write out the port. Unlike the rest of the protocol, address and port is in big endian byte order.
        stream.write((byte) (0xFF & port >> 8));
        stream.write((byte) (0xFF & port));
    }
    
    public String getHostname() {
        return hostname;
    }

    public InetAddress getAddr() {
        return addr;
    }

    public InetSocketAddress getSocketAddress() {
        return new InetSocketAddress(getAddr(), getPort());
    }

    public void setAddr(InetAddress addr) {
        this.addr = addr;
    }

    public int getPort() {
        return port;
    }

    public void setPort(int port) {
        this.port = port;
    }

    public BigInteger getServices() {
        return services;
    }

    public void setServices(BigInteger services) {
        this.services = services;
    }

    public long getTime() {
        return time;
    }

    public void setTime(long time) {
        this.time = time;
    }

    @Override
    public String toString() {
        if (hostname != null) {
            return "[" + hostname + "]:" + port;
        }
        return "[" + addr.getHostAddress() + "]:" + port;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PeerAddress other = (PeerAddress) o;
        return other.addr.equals(addr) && other.port == port && other.time == time && other.services.equals(services);
        //TODO: including services and time could cause same peer to be added multiple times in collections
    }

    @Override
    public int hashCode() {
        return Objects.hash(addr, port, time, services);
    }
    
    public InetSocketAddress toSocketAddress() {
        // Reconstruct the InetSocketAddress properly
        if (hostname != null) {
            return InetSocketAddress.createUnresolved(hostname, port);
        } else {
            return new InetSocketAddress(addr, port);
        }
    }
}
