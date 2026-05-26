import 'dart:async';
import 'dart:convert';
import 'dart:math';

import 'package:flutter/material.dart';
import 'package:http/http.dart' as http;
import 'package:sensors_plus/sensors_plus.dart';
import 'package:web_socket_channel/web_socket_channel.dart';

void main() {
  runApp(const ImuCrashApp());
}

class ImuCrashApp extends StatelessWidget {
  const ImuCrashApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      debugShowCheckedModeBanner: false,
      title: 'IMU Crash Detection',
      theme: ThemeData(
        colorScheme: ColorScheme.fromSeed(seedColor: const Color(0xFF2563EB)),
        useMaterial3: true,
      ),
      home: const CrashDetectionPage(),
    );
  }
}

class ImuSample {
  const ImuSample({
    required this.ax,
    required this.ay,
    required this.az,
    required this.gx,
    required this.gy,
    required this.gz,
    required this.timestamp,
  });

  final double ax;
  final double ay;
  final double az;
  final double gx;
  final double gy;
  final double gz;
  final DateTime timestamp;

  Map<String, double> toJson() => {
        'ax': ax,
        'ay': ay,
        'az': az,
        'gx': gx,
        'gy': gy,
        'gz': gz,
      };
}

class PredictionResult {
  const PredictionResult({
    required this.anomalyScore,
    required this.isAnomaly,
    required this.threshold,
    required this.modelVersion,
  });

  final double anomalyScore;
  final bool isAnomaly;
  final double threshold;
  final String? modelVersion;

  factory PredictionResult.fromJson(Map<String, dynamic> json) {
    return PredictionResult(
      anomalyScore: (json['anomaly_score'] as num).toDouble(),
      isAnomaly: json['is_anomaly'] as bool,
      threshold: (json['threshold'] as num).toDouble(),
      modelVersion: json['model_version'] as String?,
    );
  }
}

class ServerConfig {
  const ServerConfig({
    required this.threshold,
    required this.modelVersion,
    required this.timesteps,
    required this.featureOrder,
  });

  final double threshold;
  final String? modelVersion;
  final int timesteps;
  final List<String> featureOrder;

  factory ServerConfig.fromJson(Map<String, dynamic> json) {
    return ServerConfig(
      threshold: (json['threshold'] as num).toDouble(),
      modelVersion: json['model_version'] as String?,
      timesteps: json['timesteps'] as int,
      featureOrder: List<String>.from(json['feature_order'] as List),
    );
  }
}

class ImuWindowBuffer {
  ImuWindowBuffer({
    this.windowSize = 200,
    this.overlap = 0.50,
  }) : _stepSize = max(1, (windowSize * (1.0 - overlap)).round());

  final int windowSize;
  final double overlap;
  final int _stepSize;
  final List<ImuSample> _samples = [];

  void add(ImuSample sample) {
    _samples.add(sample);
    if (_samples.length > windowSize * 2) {
      _samples.removeRange(0, _samples.length - windowSize * 2);
    }
  }

  bool get hasWindow => _samples.length >= windowSize;

  List<ImuSample> nextWindow() {
    if (!hasWindow) {
      throw StateError('Not enough IMU samples to create a window.');
    }
    final window = List<ImuSample>.unmodifiable(_samples.take(windowSize));
    _samples.removeRange(0, min(_stepSize, _samples.length));
    return window;
  }

  int get length => _samples.length;
}

class CrashDetectionApi {
  CrashDetectionApi({
    required this.baseUrl,
    http.Client? client,
  }) : _client = client ?? http.Client();

  final String baseUrl;
  final http.Client _client;

  Future<PredictionResult> predict(List<ImuSample> window) async {
    final uri = Uri.parse('$baseUrl/predict');
    final response = await _client
        .post(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'samples': window.map((sample) => sample.toJson()).toList(),
          }),
        )
        .timeout(const Duration(seconds: 5));

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('Backend returned ${response.statusCode}: ${response.body}');
    }

    return PredictionResult.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<ServerConfig> fetchConfig() async {
    final uri = Uri.parse('$baseUrl/config');
    final response = await _client.get(uri).timeout(const Duration(seconds: 5));
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('Config request failed with ${response.statusCode}: ${response.body}');
    }
    return ServerConfig.fromJson(jsonDecode(response.body) as Map<String, dynamic>);
  }

  Future<void> uploadWindow({
    required String deviceId,
    required List<ImuSample> window,
    required PredictionResult prediction,
  }) async {
    final uri = Uri.parse('$baseUrl/windows');
    final response = await _client
        .post(
          uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'device_id': deviceId,
            'samples': window.map((sample) => sample.toJson()).toList(),
            'model_version': prediction.modelVersion,
            'anomaly_score': prediction.anomalyScore,
            'predicted_anomaly': prediction.isAnomaly,
            'user_confirmed_crash': false,
          }),
        )
        .timeout(const Duration(seconds: 5));

    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw HttpException('Window upload failed with ${response.statusCode}: ${response.body}');
    }
  }
}

class HttpException implements Exception {
  const HttpException(this.message);

  final String message;

  @override
  String toString() => message;
}

class CrashDetectionPage extends StatefulWidget {
  const CrashDetectionPage({super.key});

  @override
  State<CrashDetectionPage> createState() => _CrashDetectionPageState();
}

class _CrashDetectionPageState extends State<CrashDetectionPage> {
  // Android emulator: http://10.0.2.2:8000
  // Physical phone: use your computer's LAN IP, for example http://192.168.1.10:8000
  final CrashDetectionApi _api = CrashDetectionApi(baseUrl: 'http://10.0.2.2:8000');
  final ImuWindowBuffer _buffer = ImuWindowBuffer(windowSize: 200, overlap: 0.50);
  final String _deviceId = 'phone_demo_001';

  StreamSubscription<AccelerometerEvent>? _accelSub;
  StreamSubscription<GyroscopeEvent>? _gyroSub;
  WebSocketChannel? _configChannel;
  StreamSubscription<dynamic>? _configSub;

  AccelerometerEvent? _latestAccel;
  GyroscopeEvent? _latestGyro;
  Timer? _samplingTimer;

  bool _isCollecting = false;
  bool _isSending = false;
  String? _networkError;
  PredictionResult? _lastPrediction;
  ServerConfig? _serverConfig;
  int _windowsSent = 0;

  @override
  void dispose() {
    _stopCollection();
    super.dispose();
  }

  void _startCollection() {
    if (_isCollecting) return;
    unawaited(_refreshConfig());
    _connectConfigUpdates();

    _accelSub = accelerometerEventStream().listen((event) {
      _latestAccel = event;
    });
    _gyroSub = gyroscopeEventStream().listen((event) {
      _latestGyro = event;
    });

    // Sample both latest sensor readings onto a common 50 Hz timeline.
    _samplingTimer = Timer.periodic(const Duration(milliseconds: 20), (_) {
      final accel = _latestAccel;
      final gyro = _latestGyro;
      if (accel == null || gyro == null) return;

      _buffer.add(
        ImuSample(
          ax: accel.x,
          ay: accel.y,
          az: accel.z,
          gx: gyro.x,
          gy: gyro.y,
          gz: gyro.z,
          timestamp: DateTime.now(),
        ),
      );

      if (_buffer.hasWindow && !_isSending) {
        final window = _buffer.nextWindow();
        unawaited(_sendWindow(window));
      }

      if (mounted) setState(() {});
    });

    setState(() {
      _isCollecting = true;
      _networkError = null;
    });
  }

  void _stopCollection() {
    _samplingTimer?.cancel();
    _accelSub?.cancel();
    _gyroSub?.cancel();
    _configSub?.cancel();
    _configChannel?.sink.close();
    _samplingTimer = null;
    _accelSub = null;
    _gyroSub = null;
    _configSub = null;
    _configChannel = null;
    if (mounted) {
      setState(() {
        _isCollecting = false;
      });
    }
  }

  Future<void> _sendWindow(List<ImuSample> window) async {
    setState(() {
      _isSending = true;
      _networkError = null;
    });

    try {
      final prediction = await _api.predict(window);
      await _api.uploadWindow(
        deviceId: _deviceId,
        window: window,
        prediction: prediction,
      );
      if (!mounted) return;
      setState(() {
        _lastPrediction = prediction;
        _windowsSent += 1;
      });

      if (prediction.isAnomaly && mounted) {
        _showCrashAlert(prediction);
      }
    } on TimeoutException {
      if (mounted) {
        setState(() {
          _networkError = 'Request timed out. Check that the FastAPI server is reachable.';
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _networkError = 'Network error: $error';
        });
      }
    } finally {
      if (mounted) {
        setState(() {
          _isSending = false;
        });
      }
    }
  }

  Future<void> _refreshConfig() async {
    try {
      final config = await _api.fetchConfig();
      if (mounted) {
        setState(() {
          _serverConfig = config;
        });
      }
    } catch (error) {
      if (mounted) {
        setState(() {
          _networkError = 'Could not fetch server config: $error';
        });
      }
    }
  }

  void _connectConfigUpdates() {
    if (_configChannel != null) return;
    final wsUrl = _api.baseUrl.replaceFirst('http://', 'ws://').replaceFirst('https://', 'wss://');
    final channel = WebSocketChannel.connect(Uri.parse('$wsUrl/ws/config'));
    _configChannel = channel;
    _configSub = channel.stream.listen(
      (message) {
        final payload = jsonDecode(message as String) as Map<String, dynamic>;
        final configJson = payload['config'] == null ? payload : payload['config'] as Map<String, dynamic>;
        if (mounted) {
          setState(() {
            _serverConfig = ServerConfig.fromJson(configJson);
          });
        }
      },
      onError: (error) {
        if (mounted) {
          setState(() {
            _networkError = 'Config update stream failed: $error';
          });
        }
      },
    );
  }

  void _showCrashAlert(PredictionResult prediction) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        backgroundColor: Colors.red.shade700,
        behavior: SnackBarBehavior.floating,
        content: Text(
          'Crash anomaly detected. Score ${prediction.anomalyScore.toStringAsFixed(4)}',
        ),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final prediction = _lastPrediction;
    final isAlert = prediction?.isAnomaly ?? false;

    return Scaffold(
      appBar: AppBar(
        title: const Text('IMU Crash Detection'),
      ),
      body: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            _StatusPanel(
              isCollecting: _isCollecting,
              isSending: _isSending,
              bufferedSamples: _buffer.length,
              windowsSent: _windowsSent,
              serverConfig: _serverConfig,
            ),
            const SizedBox(height: 12),
            _PredictionPanel(
              prediction: prediction,
              isAlert: isAlert,
            ),
            if (_networkError != null) ...[
              const SizedBox(height: 12),
              _ErrorBanner(message: _networkError!),
            ],
            const Spacer(),
            FilledButton.icon(
              onPressed: _isCollecting ? _stopCollection : _startCollection,
              icon: Icon(_isCollecting ? Icons.stop : Icons.play_arrow),
              label: Text(_isCollecting ? 'Stop monitoring' : 'Start monitoring'),
            ),
          ],
        ),
      ),
    );
  }
}

class _StatusPanel extends StatelessWidget {
  const _StatusPanel({
    required this.isCollecting,
    required this.isSending,
    required this.bufferedSamples,
    required this.windowsSent,
    required this.serverConfig,
  });

  final bool isCollecting;
  final bool isSending;
  final int bufferedSamples;
  final int windowsSent;
  final ServerConfig? serverConfig;

  @override
  Widget build(BuildContext context) {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text('Status', style: Theme.of(context).textTheme.titleMedium),
            const SizedBox(height: 8),
            Text('Collecting: ${isCollecting ? "yes" : "no"}'),
            Text('Sending window: ${isSending ? "yes" : "no"}'),
            Text('Buffered samples: $bufferedSamples / 200'),
            Text('Windows sent: $windowsSent'),
            Text('Server threshold: ${serverConfig?.threshold.toStringAsFixed(5) ?? "--"}'),
            Text('Model version: ${serverConfig?.modelVersion ?? "static"}'),
          ],
        ),
      ),
    );
  }
}

class _PredictionPanel extends StatelessWidget {
  const _PredictionPanel({
    required this.prediction,
    required this.isAlert,
  });

  final PredictionResult? prediction;
  final bool isAlert;

  @override
  Widget build(BuildContext context) {
    final scoreText = prediction == null ? '--' : prediction!.anomalyScore.toStringAsFixed(5);
    final thresholdText = prediction == null ? '--' : prediction!.threshold.toStringAsFixed(5);

    return Card(
      color: isAlert ? Colors.red.shade50 : null,
      child: Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Text(
              isAlert ? 'Crash alert' : 'Prediction',
              style: Theme.of(context).textTheme.titleMedium,
            ),
            const SizedBox(height: 8),
            Text('Anomaly score: $scoreText'),
            Text('Threshold: $thresholdText'),
            Text('Status: ${isAlert ? "anomalous" : "normal"}'),
          ],
        ),
      ),
    );
  }
}

class _ErrorBanner extends StatelessWidget {
  const _ErrorBanner({required this.message});

  final String message;

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(12),
      decoration: BoxDecoration(
        color: Colors.orange.shade100,
        borderRadius: BorderRadius.circular(8),
      ),
      child: Text(message),
    );
  }
}
