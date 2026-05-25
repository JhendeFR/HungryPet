#include "esp_camera.h"
#include <WiFi.h>
#include <FirebaseESP32.h>
#include "mbedtls/base64.h"
#include <ESP32Servo.h> // Librería de servos para ESP32

// ===== CONFIGURA TU WIFI =====
const char* ssid = "SI QUIERES WIFI ROBA";
const char* password = "Pisoooo2";

// ===== CONFIGURA TU FIREBASE =====
#define DATABASE_URL "esp32-b8c7c-default-rtdb.firebaseio.com"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig configFirebase;

// ===== PINES PARA SERVOMOTORES =====
#define SERVO_1_PIN 14
#define SERVO_2_PIN 13

Servo servo1;
Servo servo2;

// ===== Pines para ESP32-CAM AI Thinker (OV2640) =====
#define PWDN_GPIO_NUM     32
#define RESET_GPIO_NUM    -1
#define XCLK_GPIO_NUM      0
#define SIOD_GPIO_NUM     26
#define SIOC_GPIO_NUM     27
#define Y9_GPIO_NUM       35
#define Y8_GPIO_NUM       34
#define Y7_GPIO_NUM       39
#define Y6_GPIO_NUM       36
#define Y5_GPIO_NUM       21
#define Y4_GPIO_NUM       19
#define Y3_GPIO_NUM       18
#define Y2_GPIO_NUM        5
#define VSYNC_GPIO_NUM    25
#define HREF_GPIO_NUM     23
#define PCLK_GPIO_NUM     22
#define LED_GPIO_NUM       4

void setup() {
    Serial.begin(115200);
    Serial.println();

    // 1. INICIALIZAR CÁMARA (Hacerlo primero para evitar conflictos de timers)
    camera_config_t config;
    config.ledc_channel = LEDC_CHANNEL_0;
    config.ledc_timer   = LEDC_TIMER_0;
    config.pin_d0       = Y2_GPIO_NUM;
    config.pin_d1       = Y3_GPIO_NUM;
    config.pin_d2       = Y4_GPIO_NUM;
    config.pin_d3       = Y5_GPIO_NUM;
    config.pin_d4       = Y6_GPIO_NUM;
    config.pin_d5       = Y7_GPIO_NUM;
    config.pin_d6       = Y8_GPIO_NUM;
    config.pin_d7       = Y9_GPIO_NUM;
    config.pin_xclk     = XCLK_GPIO_NUM;
    config.pin_pclk     = PCLK_GPIO_NUM;
    config.pin_vsync    = VSYNC_GPIO_NUM;
    config.pin_href     = HREF_GPIO_NUM;
    config.pin_sscb_sda = SIOD_GPIO_NUM;
    config.pin_sscb_scl = SIOC_GPIO_NUM;
    config.pin_pwdn     = PWDN_GPIO_NUM;
    config.pin_reset    = RESET_GPIO_NUM;
    config.xclk_freq_hz = 20000000;
    config.pixel_format = PIXFORMAT_JPEG;
    config.frame_size = FRAMESIZE_QQVGA;
    config.jpeg_quality = 15;
    config.fb_count = 1;

    esp_err_t err = esp_camera_init(&config);
    if (err != ESP_OK) {
        Serial.printf("Error inicializando la cámara: 0x%x\n", err);
        return;
    }
    sensor_t * s = esp_camera_sensor_get();
    s->set_vflip(s, 1);
    s->set_hmirror(s, 1);

    // 2. INICIALIZAR SERVOMOTORES
    // Asignamos timers específicos para que no choquen con la cámara
    ESP32PWM::allocateTimer(1);
    ESP32PWM::allocateTimer(2);

    servo1.setPeriodHertz(50); // Frecuencia estándar para servos
    servo2.setPeriodHertz(50);

    servo1.attach(SERVO_1_PIN, 500, 2400);
    servo2.attach(SERVO_2_PIN, 500, 2400);

    // Mover a posición inicial (0 grados)
    Serial.println("Posicionando servos en 0 grados...");
    servo1.write(0);
    servo2.write(0);
    delay(500);

    // 3. CONECTAR A WIFI
    WiFi.begin(ssid, password);
    Serial.print("Conectando a WiFi...");
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nWiFi conectado exitosamente.");

    // 4. CONECTAR A FIREBASE
    configFirebase.database_url = DATABASE_URL;
    configFirebase.signer.test_mode = true;
    fbdo.setBSSLBufferSize(4096, 4096);

    Firebase.begin(&configFirebase, &auth);
    Firebase.reconnectWiFi(true);
    Serial.println("Conectado con Firebase Realtime Database.");

    // Asegurarnos de que el nodo de disparo inicie en 0
    Firebase.setInt(fbdo, "/dispensador/activar", 0);
}

void loop() {
    if (WiFi.status() == WL_CONNECTED) {

        // ==========================================
        // PARTE A: REVISAR COMANDOS DE DISPENSACIÓN
        // ==========================================
        if (Firebase.getInt(fbdo, "/dispensador/activar")) {
            if (fbdo.intData() == 1) {
                Serial.println("¡Comando recibido! Dispensando alimento...");

                // Abrir compuertas (90 grados)
                servo1.write(120);
                delay(88);
                servo2.write(120);

                delay(1500); // Tiempo que las compuertas se quedan abiertas

                // Cerrar compuertas (0 grados)
                servo1.write(0);
                servo2.write(0);

                // Reiniciar el comando en Firebase a 0
                Firebase.setInt(fbdo, "/dispensador/activar", 0);
                Serial.println("Dispensación completada. Compuertas cerradas.");
            }
        }

        // ==========================================
        // PARTE B: CAPTURAR Y ENVIAR IMAGEN
        // ==========================================
        camera_fb_t * fb = esp_camera_fb_get();
        if (!fb) {
            Serial.println("Error al capturar el frame");
            delay(1000);
            return;
        }

        size_t base64_len = 4 * ((fb->len + 2) / 3) + 1;
        unsigned char* base64_buffer = (unsigned char*)ps_malloc(base64_len);

        if (base64_buffer) {
            size_t output_len = 0;
            int ret = mbedtls_base64_encode(base64_buffer, base64_len, &output_len, fb->buf, fb->len);

            if (ret == 0) {
                base64_buffer[output_len] = '\0';
                String base64_str = String((char*)base64_buffer);

                // Subir a Firebase
                if (Firebase.setString(fbdo, "/camara/stream", base64_str)) {
                    // Oculté este print para que la consola no se sature, pero funciona igual
                    // Serial.println("Frame transmitido.");
                } else {
                    Serial.print("Error al subir a Firebase: ");
                    Serial.println(fbdo.errorReason());
                }
            }
            free(base64_buffer);
        }
        esp_camera_fb_return(fb);
    }

    // El delay general del ciclo
    delay(1500);
}