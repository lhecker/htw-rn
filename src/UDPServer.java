import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.CRC32;

class UDPServer extends UDPBase {
	private static double _packetLoss;
	private static double _packetDelay;
	private static double _packetDelayVariation;

	/**
	 * Used in a testing environment to simulate packet delay and loss.
	 *
	 * Use this function in the beginning of functions that receive or send data.
	 * This function will first put the current thread to sleep
	 * for a random amount of time obtained using the
	 * _packetDelay and _packetDelayVariation values.
	 * The delay is normally distributed around the _packetDelay value.
	 * After that the function will return a value, specifying
	 * if the packet should be dropped. This value is obtained using
	 * the _packetLoss value and uniformly distributed.
	 *
	 * @return true if the packet should be dropped, otherwise false.
	 */
	private static boolean simulateDelayAndLoss() {
		double delay = _packetDelay;

		if (_packetDelayVariation > 0) {
			delay += _rand.nextGaussian() * _packetDelayVariation;
		}

		if (delay > 0.0) {
			try {
				Thread.sleep((long) Math.round(delay));
			} catch (InterruptedException e) {
			}
		}

		return _packetLoss > 0 && _rand.nextDouble() < _packetLoss;
	}

	/**
	 * Tries to receive a single packet.
	 *
	 * This function will try to receive a packet.
	 *
	 * @throws IOException
	 */
	private static void receive() throws IOException {
		do {
			_rxd.clear();
			_socket.receive(_rxp);
			_rxd.limit(_rxp.getLength());
		} while (UDPServer.simulateDelayAndLoss());
	}

	/**
	 * Sends a single ACK to the current client.
	 *
	 * @param packetId The packet which should be acknowledged.
	 * @throws IOException
	 */
	private static void sendACK(byte packetId) throws IOException {
		if (!UDPServer.simulateDelayAndLoss()) {
			ByteBuffer txd = ByteBuffer.allocate(3);
			txd.putShort(_sessionId);
			txd.put(packetId);

			DatagramPacket packet = new DatagramPacket(txd.array(), txd.capacity(), _targetAddress);
			_socket.send(packet);
		}
	}

	private static File createFileForFilenameWish(byte[] f) throws Exception {
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

	private static void printHelp() {
		System.out.println("Usage: server-udp <port> [<loss> <delay> [<veriation>]]");
		System.out.println("  <port>       The port number the server should listen on.");
		System.out.println("               This number must be between 0 and 65535");
		System.out.println("               A value of 0 tells the application to choose a random port.");
		System.out.println("  <loss>       The average packet loss.");
		System.out.println("               This must be a number between 0 and 1, or 0% and 100% (inclusive).");
		System.out.println("  <delay>      The average packet delay in ms.");
		System.out.println("               Either with or without a ms suffix.");
		System.out.println("               The default is 0.");
		System.out.println("  <variation>  The random variation of the delay value.");
		System.out.println("               This value can either be an absolute amount in ms, or relative to delay");
		System.out.println("               as a value between 0 and 1, or 0% and 100% (inclusive, the default).");
		System.out.println("               A variation of 10ms and a delay of 100ms will create a random delay of 100±10ms.");
		System.out.println("               The default is 10%.");
	}

	public static void main(String args[]) throws Exception {
		switch (args.length) {
		case 1:
		case 3:
		case 4:
			break;
		default:
			UDPServer.printHelp();
			System.exit(1);
		}

		int port = 0;

		try {
			port = Integer.parseInt(args[0]);

			if (port < 0 || port > 65535) {
				throw new Exception();
			}
		} catch (Exception e) {
			System.err.println("port outside of valid range [0, 65535]");
			UDPServer.printHelp();
			System.exit(2);
		}

		if (args.length > 1) {
			try {
				Matcher m = Pattern.compile("([\\d.]+)(%)?").matcher(args[1]);

				if (!m.matches()) {
					throw new Exception();
				}

				_packetLoss = Double.parseDouble(m.group(1));

				if (m.group(2) != null) {
					_packetLoss /= 100.0;
				}

				if (_packetLoss > 1) {
					throw new Exception();
				}
			} catch (Exception e) {
				System.err.println("loss outside of valid range [0,1]");
				UDPServer.printHelp();
				System.exit(2);
			}

			try {
				Matcher m = Pattern.compile("([\\d.]+)(ms)?").matcher(args[2]);

				if (!m.matches()) {
					throw new Exception();
				}

				_packetDelay = Double.parseDouble(m.group(1));
			} catch (Exception e) {
				System.err.println("delay outside of valid range [0,∞)ms");
				UDPServer.printHelp();
				System.exit(2);
			}

			if (args.length > 3) {
				try {
					Matcher m = Pattern.compile("([\\d.]+)(%|ms)?").matcher(args[3]);

					if (!m.matches()) {
						throw new Exception();
					}

					_packetDelayVariation = Double.parseDouble(m.group(1));

					String suffix = m.group(2);

					if (suffix == null) {
						if (_packetDelayVariation > 1.0) {
							throw new Exception();
						}

						_packetDelayVariation = _packetDelayVariation * _packetDelay;
					} else if (suffix.equals("%")) {
						if (_packetDelayVariation > 100.0) {
							throw new Exception();
						}

						_packetDelayVariation = (_packetDelayVariation / 100.0) * _packetDelay;
					}
				} catch (Exception e) {
					System.err.println("variation outside of valid range [0,∞)ms, [0,1], or [0,100]%");
					UDPServer.printHelp();
					System.exit(2);
				}
			}
		}

		try {
			_socket = new DatagramSocket(port);
		} catch (Exception e) {
			System.err.println("Failed to create an DatagramSocket!");
			System.err.println(e.getMessage());
			System.exit(3);
		}

		// Java's CRC32 uses the IEEE 0x04C11DB7 polynomial
		final CRC32 cc = new CRC32();
		final byte[] start = { 'S', 't', 'a', 'r', 't' };

		mainloop:
		while (true) {
			_socket.setSoTimeout(0);
			UDPServer.receive();
			_socket.setSoTimeout(UDPBase.PACKET_TIMEOUT_SUM);

			_targetAddress = (InetSocketAddress) _rxp.getSocketAddress();

			// reset ---> the next call to getNextPacketId() will return 0
			_packetId = UDPBase.PACKET_ID_MAX;

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

			_sessionId = h_sessionId;

			UDPServer.sendACK(h_packetId);

			final File file = UDPServer.createFileForFilenameWish(h_filename);

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

					if (!d_address.equals(_targetAddress)) {
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

					if (d_sessionId != _sessionId) {
						throw new Exception("invalid session id");
					}

					if (d_packetId != UDPServer.getNextPacketId()) {
						System.err.println("[warning] data: invalid packet id");
						continue;
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

					UDPServer.sendACK(d_packetId);
				}
			} catch (Exception e) {
				System.err.println("[error] data: " + e.getMessage());
				file.delete();
			}
		}
	}
}
