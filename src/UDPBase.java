import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.nio.ByteBuffer;
import java.util.Random;

public class UDPBase {
	protected static final byte PACKET_ID_MAX = 1;
	protected static final byte PACKET_RESEND_MAX = 10;

	protected static Random _rand = new Random();
	protected static ByteBuffer _rxd = ByteBuffer.allocate(64 * 1024);
	protected static DatagramPacket _rxp = new DatagramPacket(_rxd.array(), _rxd.capacity());

	protected static DatagramSocket _socket;

	protected static byte _packetId = PACKET_ID_MAX;

	protected static byte getNextPacketId() {
		_packetId = (byte) ((_packetId + 1) % (PACKET_ID_MAX + 1));
		return _packetId;
	}
}
