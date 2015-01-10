import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.util.Random;

public class UDPBase {
	protected static final int PACKET_ID_MAX = 1;
	protected static final int PACKET_RETRY_MAX = 10;
	protected static final int PACKET_TIMEOUT_MIN = 10;
	protected static final int PACKET_TIMEOUT_MAX = 3000;

	/*
	 * We assume that updateSoTimeout(i) will be called
	 * PACKET_RETRY_MAX times from i=1 to i=PACKET_RETRY_MAX.
	 * Every time the timeout will be increased by a value x*i.
	 * In the worst case we need to reach PACKET_TIMEOUT_MAX,
	 * starting at PACKET_TIMEOUT_MIN within PACKET_RETRY_MAX
	 * invocations. This means we need a value x, so:
	 *   PACKET_TIMEOUT_MAX = PACKET_TIMEOUT_MIN + x*1 + x*2 + ... + x*PACKET_RETRY_MAX
	 * If x = 1, we can use the sum over the natural numbers from 1 to n:
	 *   n(n+1) / 2
	 * Place x in the formulae again and we get what's below (+1 to round up)
	 */
	protected static final int PACKET_TIMEOUT_STEP = (int) Math.ceil((double) (PACKET_TIMEOUT_MAX - PACKET_TIMEOUT_MIN) / ((PACKET_RETRY_MAX * (PACKET_RETRY_MAX + 1)) / 2.0));

	protected static final int PACKET_TIMEOUT_SUM;

	protected static Random _rand = new Random();
	protected static ByteBuffer _rxd = ByteBuffer.allocate(64 * 1024);
	protected static DatagramPacket _rxp = new DatagramPacket(_rxd.array(), _rxd.capacity());

	protected static DatagramSocket _socket;
	protected static InetSocketAddress _targetAddress;

	protected static short _sessionId;
	protected static byte _packetId = PACKET_ID_MAX;
	
	static {
		int sum = 0;
		int partialSum = PACKET_TIMEOUT_MIN;

		for (int i = 1; i <= PACKET_RETRY_MAX; i++) {
			partialSum += i * PACKET_TIMEOUT_STEP;
			sum += partialSum;
		}

		PACKET_TIMEOUT_SUM = sum;
	}

	protected static byte getNextPacketId() {
		_packetId = (byte) ((_packetId + 1) % (PACKET_ID_MAX + 1));
		return _packetId;
	}
}
