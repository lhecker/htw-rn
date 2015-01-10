import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketTimeoutException;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.Enumeration;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicLong;
import java.util.zip.CRC32;

class UDPClient extends UDPBase {
	private static long _totalBytes;
	private static AtomicLong _finishedBytes = new AtomicLong();
	private static long _previousBytes;
	private static long _previousTime;
	private static int _previousStatWidth;

	protected static int _srtt = Integer.MAX_VALUE;
	protected static int _rttvar;
	protected static int _rto = PACKET_TIMEOUT_MAX;

	static {
		_sessionId = (short) _rand.nextInt(0x10000);
	}

	protected static void updateRtoWithRtt(int rtt) {
		if (rtt < 0) {
			return;
		}

		if (_srtt == Integer.MAX_VALUE) {
			_rttvar = rtt / 2;
			_srtt = rtt;
			_rto = _srtt + 4 * _rttvar;
		} else {
			_rttvar = (3 * _rttvar + Math.abs(_srtt - rtt)) / 4;
			_srtt = (7 * _srtt + rtt) / 8;
			_rto = _srtt + 4 * _rttvar;
		}

		_rto += PACKET_TIMEOUT_MIN;

		if (_rto > PACKET_TIMEOUT_MAX) {
			_rto = PACKET_TIMEOUT_MAX;
		}

		try {
			_socket.setSoTimeout(_rto);
		} catch (Exception e) {
		}
	}

	protected static void updateRtoWithTimeout() {
		_rto *= 2;

		if (_rto < PACKET_TIMEOUT_MIN) {
			_rto = PACKET_TIMEOUT_MIN;
		} else if (_rto > PACKET_TIMEOUT_MAX) {
			_rto = PACKET_TIMEOUT_MAX;
		}

		try {
			_socket.setSoTimeout(_rto);
		} catch (Exception e) {
		}
	}

	private static void send(ByteBuffer txd) throws IOException {
		DatagramPacket txp = new DatagramPacket(txd.array(), txd.limit(), _targetAddress);
		int i = 0;

		long time1 = System.nanoTime();

		while (true) {
			try {
				_socket.send(txp);

				_rxd.clear();
				_socket.receive(_rxp);
				_rxd.limit(_rxp.getLength());

				if (_rxd.limit() != 3) {
					throw new IOException("ACK: invalid size");
				}

				short sessionId = _rxd.getShort();
				byte packetId = _rxd.get();

				if (sessionId != _sessionId) {
					throw new IOException("ACK: invalid session id");
				}

				if (packetId != UDPClient.packetId()) {
					continue;
				}

				if (i == 0) {
					long time2 = System.nanoTime();
					int rtt = (int) ((time2 - time1) / 1000000);
					UDPClient.updateRtoWithRtt(rtt);
				}

				break;
			} catch (SocketTimeoutException e) {
				if (++i == UDPBase.PACKET_RETRY_MAX) {
					throw e;
				}

				UDPClient.updateRtoWithTimeout();
			}
		}
	}

	private static String formatSize(double size) {
		final String prefixes = "kMGTPE";
		double sizeExp = Math.floor(Math.log(size) / Math.log(1000));
		String sizePrefix;

		try {
			int idx = (int) sizeExp;
			sizePrefix = prefixes.substring(idx - 1, idx);
			return String.format("%.2f%sB", size / Math.pow(1000, sizeExp), sizePrefix);
		} catch (StringIndexOutOfBoundsException e) {
			return String.format("%.0fB", size);
		}
	}

	private static void showStats() {
		final long time = System.nanoTime();
		final long finishedBytes = _finishedBytes.get();
		final double finishedDelta = finishedBytes - _previousBytes;
		final double timeDelta = time - _previousTime;
		final double speed = 1e9 * finishedDelta / timeDelta;
		final double percent = (double) finishedBytes / _totalBytes;

		final int barWidth = (int) Math.round(percent * 50);
		final char[] barData = new char[50];
		Arrays.fill(barData, barWidth, 50, ' ');
		Arrays.fill(barData, 0, barWidth, '=');

		if (barWidth > 0 && barWidth < 50) {
			barData[barWidth] = '>';
		}

		final String bar = new String(barData);
		int etaSec = (int) ((_totalBytes - finishedBytes) / speed);
		int etaMin;

		if (etaSec < 0 || etaSec == Integer.MAX_VALUE) {
			etaMin = 0;
			etaSec = 0;
		} else {
			etaMin = etaSec / 60;
			etaSec %= 60;
		}

		final String stat = String.format("%3.0f%% [%s] %,d  %s/s  eta %dm %ds", percent * 100, bar, finishedBytes, UDPClient.formatSize(speed), etaMin, etaSec);
		final int statWidth = stat.length();
		final int statWidthDiff = _previousStatWidth - statWidth;
		String statWitespacePadding;

		if (statWidthDiff > 0) {
			final char[] paddingData = new char[statWidthDiff];
			Arrays.fill(paddingData, ' ');
			statWitespacePadding = new String(paddingData);
		} else {
			statWitespacePadding = "";
		}

		System.out.printf("\r%s%s", stat, statWitespacePadding);

		_previousTime = time;
		_previousBytes = finishedBytes;
		_previousStatWidth = statWidth;
	}

	public static void main(String args[]) throws Exception {
		if (args.length != 3) {
			System.out.println("Usage: client-udp <host> <port> <filepath>");
			return;
		}

		_socket = new DatagramSocket();
		_socket.setSoTimeout(PACKET_TIMEOUT_MAX);
		_targetAddress = new InetSocketAddress(args[0], Short.parseShort(args[1]));

		if (_targetAddress.isUnresolved()) {
			System.err.println("[error] Cannot resolve: " + args[0] + ':' + args[1]);
			return;
		}

		final Enumeration<NetworkInterface> inets = NetworkInterface.getNetworkInterfaces();
		int mtu = Integer.MAX_VALUE;

		/*
		 *  TODO: Improve the precision of mtu by getting the MTU
		 *  of the NIC the packets are going to be sent on.
		 */
		while (inets.hasMoreElements()) {
			int newMtu = inets.nextElement().getMTU();

			if (newMtu != -1 && newMtu < mtu) {
				mtu = newMtu;
			}
		}

		if (mtu == Integer.MAX_VALUE) {
			mtu = 1500;
		}

		/* As per RFC 791:
		 * "Every internet module must be able to forward
		 * a datagram of 68 octets without further fragmentation."
		 */
		if (mtu < 68) {
			System.err.println("[error] MTU smaller than required IPv4 minimum size.");
			return;
		}

		final File file = new File(args[2]);

		if (!file.isFile()) {
			System.err.println("[error] File not found or not readable: " + args[2]);
			return;
		}

		final String filename = file.getName();
		final byte[] filenameData = filename.getBytes("UTF-8");

		if (filenameData.length > 255) {
			System.err.println("[error] Filename too long: '" + filename + "'");
			return;
		}

		_totalBytes = file.length();

		// 40 Byte IPv6 Header size + 8 Byte UDP Header size
		mtu -= 48;

		// Java's CRC32 uses the IEEE 0x04C11DB7 polynomial
		final CRC32 cc = new CRC32();
		final Timer timer = new Timer();
		ByteBuffer txd;

		try (final FileInputStream fin = new FileInputStream(file)) {
			System.out.print("Connecting to " + _targetAddress.getAddress().getHostAddress() + ":" + _targetAddress.getPort() + "... ");

			_previousTime = System.nanoTime();

			// the handshake header fields
			txd = ByteBuffer.allocate(2 + 1 + 5 + 8 + 2 + filenameData.length + 4);
			txd.putShort(_sessionId);
			txd.put(UDPServer.packetId());
			txd.put(new byte[] { 'S', 't', 'a', 'r', 't' });
			txd.putLong(_totalBytes);
			txd.putShort((short) filenameData.length);
			txd.put(filenameData);

			cc.update(txd.array(), 0, txd.position());

			txd.putInt((int) cc.getValue());

			UDPClient.send(txd);

			System.out.printf("connected.%nSending: '%s'%nLength: %,d (%s)%n%n", filename, _totalBytes, UDPClient.formatSize(_totalBytes));

			cc.reset();
			txd = ByteBuffer.allocate(mtu);

			timer.schedule(new TimerTask() {
				@Override
				public void run() {
					UDPClient.showStats();
				}
			}, 1000, 1000);

			while (true) {
				txd.clear();
				txd.putShort(_sessionId);
				txd.put(UDPServer.setPacketIdToNext());

				int remaining = txd.remaining();
				int n = fin.read(txd.array(), txd.position(), remaining);

				if (n == -1) {
					break;
				}

				cc.update(txd.array(), txd.position(), n);
				txd.position(txd.position() + n);

				if (n < remaining) {
					remaining = txd.remaining();
					n = fin.read(txd.array(), txd.position(), remaining);

					if (n != -1) {
						cc.update(txd.array(), txd.position(), n);
						txd.position(txd.position() + n);
					} else if (remaining >= 4) {
						break;
					}
				}

				txd.limit(txd.position());

				UDPClient.send(txd);

				_finishedBytes.addAndGet(n);
			}

			txd.putInt((int) cc.getValue());
			txd.limit(txd.position());

			UDPClient.send(txd);

			UDPClient.showStats();
			System.out.println();
		} catch (Exception e) {
			System.out.println();
			System.out.println();
			System.err.println("[error] " + e.getMessage());
		} finally {
			timer.cancel();
		}
	}
}
