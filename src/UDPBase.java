import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
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
