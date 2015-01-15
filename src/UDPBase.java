import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.ByteBuffer;
import java.util.Date;
import java.util.Enumeration;
import java.util.Random;

public class UDPBase {
	protected static final int PACKET_ID_COUNT = 2;

	protected static final int PACKET_RETRY_MAX = 10;
	protected static final int PACKET_TIMEOUT_MIN = 10;
	protected static final int PACKET_TIMEOUT_MAX = 2500;

	protected static final int PACKET_TIMEOUT_SERVER = 4 * PACKET_TIMEOUT_MAX;

	protected static Random _rand = new Random();
	protected static ByteBuffer _rxd = ByteBuffer.allocate(64 * 1024);
	protected static DatagramPacket _rxp = new DatagramPacket(_rxd.array(), _rxd.capacity());

	protected static DatagramSocket _socket;
	protected static InetSocketAddress _targetAddress;

	protected static short _sessionId;
	protected static byte _packetId;

	protected static void log(String msg) {
		System.out.printf("%tF %<tT ", new Date());
		System.out.println(msg);
	}

	protected static void error(String msg) {
		System.err.printf("%tF %<tT ", new Date());
		System.err.println(msg);
	}

	protected static int getMTU() {
		int mtu = Integer.MAX_VALUE;

		try {
			Enumeration<NetworkInterface> inets = NetworkInterface.getNetworkInterfaces();

			/*
			 * TODO: Improve the precision of mtu by getting the MTU
			 *  of the NIC the packets are going to be sent on.
			 */
			while (inets.hasMoreElements()) {
				NetworkInterface inet = inets.nextElement();

				if (inet.isUp()) {
					int newMtu = inet.getMTU();

					if (newMtu != -1 && newMtu < mtu) {
						mtu = newMtu;
					}
				}
			}
		} catch (Exception e) {
		}

		/*
		 * RFC 1122 specifies 576 Byte (EMTU_R) as the minimum maximum
		 * reassembly buffer size for IPv4 (and 1500 for IPv6).
		 */
		if (mtu == Integer.MAX_VALUE || mtu < 576) {
			mtu = 576;
		}

		return mtu;
	}

	protected static byte packetId() {
		return _packetId;
	}

	protected static void resetPacketId() {
		_packetId = 0;
	}

	protected static void setPacketId(byte id) {
		_packetId = id;
	}

	protected static byte nextPacketId() {
		return (byte) ((_packetId + 1) % PACKET_ID_COUNT);
	}

	protected static byte setPacketIdToNext() {
		_packetId = nextPacketId();
		return _packetId;
	}
}
