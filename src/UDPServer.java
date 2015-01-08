import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.zip.CRC32;

class UDPServer extends UDPBase {
	private static void receive() throws IOException {
		int i = 0;

		while (true) {
			try {
				_rxd.clear();
				_socket.receive(_rxp);
				_rxd.limit(_rxp.getLength());
				return;
			} catch (SocketTimeoutException e) {
				if (++i == PACKET_RESEND_MAX) {
					throw e;
				}
			}
		}
	}

	private static void sendACK(SocketAddress address, short sessionId, byte packetId) throws IOException {
		ByteBuffer txd = ByteBuffer.allocate(3);
		txd.putShort(sessionId);
		txd.put(packetId);

		DatagramPacket packet = new DatagramPacket(txd.array(), txd.capacity(), address);
		_socket.send(packet);
	}

	private static File discoverFile(byte[] f) throws Exception {
		String filename = new String(f, "UTF-8");
		File file = new File(filename);

		if (file.exists()) {
			int idx = filename.lastIndexOf('.');
			String name = filename.substring(0, idx);
			String ext = filename.substring(idx);

			for (int i = 1; i < Integer.MAX_VALUE; i++) {
				file = new File(name + String.valueOf(i) + ext);

				if (!file.exists()) {
					break;
				}
			}

			if (file.exists()) {
				throw new Exception("could not find non existing filename");
			}
		}

		return file;
	}

	public static void main(String args[]) throws Exception {
		if (args.length != 1) {
			System.out.println("Usage: server-udp [port]");
			return;
		}

		_socket = new DatagramSocket(Integer.parseInt(args[0]));

		// Java's CRC32 uses the IEEE 0x04C11DB7 polynomial
		final CRC32 cc = new CRC32();
		final byte[] start = { 'S', 't', 'a', 'r', 't' };

		mainloop:
		while (true) {
			_socket.setSoTimeout(0);
			UDPServer.receive();

			final SocketAddress h_address = _rxp.getSocketAddress();

			// TODO: implement dynamic timeouts
			_socket.setSoTimeout(1000);

			// reset ---> the next call to getNextPacketId() will return 0
			_packetId = PACKET_ID_MAX;

			/*
			 * The handshake header fields:
			 *   short h_sessionId;
			 *   byte h_packetId;
			 *   long h_length;
			 *   short h_filenameLength;
			 *   byte[] h_filename;
			 *   int h_crc32;
			 */

			/*
			 * The handshake packet is in every possible case at least 23 Bytes
			 * large. (This includes +1 for the assertion, that the filename is
			 * at least 1 Byte long.)
			 */
			if (_rxd.limit() < 23) {
				System.err.println("[error] handshake: too small");
				continue mainloop;
			}

			final short h_sessionId = _rxd.getShort();
			final byte h_packetId = _rxd.get();

			// as per specification the handshake must have a packet ID of 0
			if (h_packetId != UDPServer.getNextPacketId()) {
				System.err.println("[error] handshake: invalid packet id");
				continue mainloop;
			}

			for (int i = 0; i < start.length; i++) {
				if (_rxd.get() != start[i]) {
					System.err.println("[error] handshake: invalid \"Start\" signature");
					continue mainloop;
				}
			}

			final long h_length = _rxd.getLong();

			if (h_length <= 0) {
				System.err.println("[error] handshake: invalid (zero) or too large (greater than Long.MAX_VALUE) length field");
				continue mainloop;
			}

			final short h_filenameLength = _rxd.getShort();

			/*
			 * Check remaining() if the filename is actually fully present,
			 * including 4 additional Bytes for the CRC32.
			 */
			if (h_filenameLength <= 0 || _rxd.remaining() - 4 < h_filenameLength) {
				System.err.println("[error] handshake: invalid filename field");
				continue mainloop;
			}

			final byte[] h_filename = new byte[h_filenameLength];
			_rxd.get(h_filename);

			cc.reset();
			cc.update(_rxd.array(), 0, _rxd.position());

			final int h_crc32 = _rxd.getInt();

			/*
			 * Casting cc.getValue() down to int is very important.
			 * cc.getValue() will return some positive value [0, 2^32).
			 * _rxd.getInt() will return the same value (bitwise), but in a signed representation.
			 * Thus we can just cast the first one down to an int, to get a correct comparison.
			 * If we don't, the compiler would promote the (int) h_crc32 to an (long),
			 * which turns (int)-1 to (long)-1, instead of an positive value [0, 2^32).
			 */
			if (h_crc32 != (int) cc.getValue()) {
				System.err.println("[error] handshake: invalid checksum");
				continue mainloop;
			}

			UDPServer.sendACK(h_address, h_sessionId, h_packetId);

			final File file = UDPServer.discoverFile(h_filename);

			try (final FileOutputStream fout = new FileOutputStream(file)) {
				long remaining = h_length;

				cc.reset();

				while (remaining >= 0) {
					short d_sessionId;
					byte d_packetId;
					int d_crc32;

					try {
						UDPServer.receive();
					} catch (SocketTimeoutException e) {
						throw new Exception("timeout");
					}

					SocketAddress d_address = _rxp.getSocketAddress();

					if (!d_address.equals(h_address)) {
						System.err.println("[warning] data: interference from another client " + d_address.toString());
						continue;
					}

					/*
					 * +1 byte as an assertion that this packet
					 * contains at least a single byte of data.
					 */
					if (_rxd.limit() < 4) {
						throw new Exception("too small");
					}

					d_sessionId = _rxd.getShort();
					d_packetId = _rxd.get();

					if (d_sessionId != h_sessionId) {
						throw new Exception("invalid session id");
					}

					if (d_packetId != UDPServer.getNextPacketId()) {
						throw new Exception("invalid packet id");
					}

					int dataLength = _rxd.remaining();

					/*
					 * If remaining is -4 this packet must contain additional 4
					 * bytes at the end, which contains the CRC32 sum over all data.
					 * If remaining is 0 the next packet must only contain 4 bytes
					 * at the end, which contains the CRC32 sum over all data.
					 */
					remaining -= dataLength;

					if (remaining <= 0) {
						if (remaining == -4) {
							dataLength -= 4;
						} else if (remaining != 0) {
							throw new Exception("missing final CRC32");
						}
					}

					if (dataLength > 0) {
						cc.update(_rxd.array(), _rxd.position(), dataLength);
						fout.write(_rxd.array(), _rxd.position(), dataLength);
					}

					if (remaining == -4) {
						d_crc32 = _rxd.getInt(_rxd.position() + dataLength);

						/*
						 * Casting cc.getValue() down to int is very important.
						 * cc.getValue() will return some positive value [0, 2^32).
						 * _rxd.getInt() will return the same value (bitwise), but in a signed representation.
						 * Thus we can just cast the first one down to an int, to get a correct comparison.
						 * If we don't, the compiler would promote the (int) h_crc32 to an (long),
						 * which turns (int)-1 to (long)-1, instead of an positive value [0, 2^32).
						 */
						if (d_crc32 != (int) cc.getValue()) {
							throw new Exception("checksum not equal");
						}
					}

					UDPServer.sendACK(h_address, d_sessionId, d_packetId);
				}
			} catch (Exception e) {
				System.err.println("[error] data: " + e.getMessage());
				file.delete();
			}
		}
	}
}
