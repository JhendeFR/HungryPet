#include "esp_camera.h"
#include <WiFi.h>
#include <FirebaseESP32.h> // Tu librería instalada
#include "mbedtls/base64.h"

// ===== CONFIGURA TU WIFI =====
const char* ssid = "SI QUIERES WIFI ROBA";
const char* password = "Pisoooo2";

// ===== CONFIGURA TU FIREBASE =====
// 1. URL limpia
#define DATABASE_URL "esp32-b8c7c-default-rtdb.firebaseio.com"

// Objetos requeridos por la versión 4.4.x de la librería
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig configFirebase;

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

    WiFi.begin(ssid, password);
    Serial.print("Conectando a WiFi...");
    while (WiFi.status() != WL_CONNECTED) {
        delay(500);
        Serial.print(".");
    }
    Serial.println("\nWiFi conectado exitosamente.");

    // Configuración de Firebase para la versión 4.x
    configFirebase.database_url = DATABASE_URL;
    configFirebase.signer.test_mode = true;

    fbdo.setBSSLBufferSize(4096, 4096);

    Firebase.begin(&configFirebase, &auth);
    Firebase.reconnectWiFi(true);
    Serial.println("Conectado con Firebase Realtime Database.");
}

void loop() {
    if (WiFi.status() == WL_CONNECTED) {

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
                    Serial.println("Frame transmitido con éxito a Firebase!");
                } else {
                    Serial.print("Error al subir a Firebase: ");
                    Serial.println(fbdo.errorReason());
                }
            }
            free(base64_buffer);
        }
        esp_camera_fb_return(fb);
    }
    delay(1500);
}