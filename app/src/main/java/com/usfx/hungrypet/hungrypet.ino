#include "esp_camera.h"
#include <WiFi.h>
#include <FirebaseESP32.h>
#include "mbedtls/base64.h"
#include <ESP32Servo.h>

// ===== CONFIGURA TU WIFI =====
const char* ssid = "WiFi_para_Negros";
const char* password = "Oso12802322";

// ===== CONFIGURA TU FIREBASE =====
#define DATABASE_URL "esp32-b8c7c-default-rtdb.firebaseio.com"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig configFirebase;

// ===== PIN PARA EL SERVOMOTOR (Solo usamos 1) =====
#define SERVO_PIN 14
#define LED_GPIO_NUM 4 // Flash LED

Servo miServo;

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

void setup() {
    Serial.begin(115200);
    Serial.println();

    // 0. INICIALIZAR FLASH APAGADO
    pinMode(LED_GPIO_NUM, OUTPUT);
    digitalWrite(LED_GPIO_NUM, LOW);

    // 1. INICIALIZAR CÁMARA
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

    // 2. INICIALIZAR EL ÚNICO SERVOMOTOR
    ESP32PWM::allocateTimer(1);
    miServo.setPeriodHertz(50);
    miServo.attach(SERVO_PIN, 500, 2400);

    Serial.println("Posicionando compuerta en 0 grados...");
    miServo.write(0);
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

    Firebase.setInt(fbdo, "/dispensador/activar", 0);
    Firebase.setInt(fbdo, "/camara/flash", 0);
    Firebase.setInt(fbdo, "/camara/encendida", 0);
}

void loop() {
    if (WiFi.status() == WL_CONNECTED) {

        bool isDispensing = false;

        // ==========================================
        // PARTE A: DISPENSAR ALIMENTO
        // ==========================================
        if (Firebase.getInt(fbdo, "/dispensador/activar")) {
            int gramos = fbdo.intData();

            if (gramos > 0) {
                isDispensing = true;
                if (gramos > 150) gramos = 150;

                Serial.print("¡Comando recibido! Gramos: ");
                Serial.println(gramos);

                unsigned long tiempoApertura = (gramos * 10000UL) / 150UL;
                unsigned long inicio = millis();

                while (millis() - inicio < tiempoApertura) {
                    // Oscilación suave para 1 solo servo
                    for (int angulo = 90; angulo <= 92; angulo++) {
                        miServo.write(angulo);
                        delay(200);
                        yield();
                        if (millis() - inicio >= tiempoApertura) break;
                    }
                    if (millis() - inicio >= tiempoApertura) break;
                    for (int angulo = 92; angulo >= 90; angulo--) {
                        miServo.write(angulo);
                        delay(200);
                        yield();
                        if (millis() - inicio >= tiempoApertura) break;
                    }
                }

                // Cierre de compuerta
                miServo.write(0);

                Firebase.setInt(fbdo, "/dispensador/activar", 0);
                Serial.println("Dispensación completada.");
                delay(500);
            }
        }

        // ==========================================
        // PARTE B: CÁMARA ON-DEMAND
        // ==========================================
        if (!isDispensing) {
            if (Firebase.getInt(fbdo, "/camara/encendida")) {
                if (fbdo.intData() == 1) {
                    camera_fb_t * fb = esp_camera_fb_get();
                    if (!fb) { delay(500); return; }

                    size_t base64_len = 4 * ((fb->len + 2) / 3) + 1;
                    unsigned char* base64_buffer = (unsigned char*)ps_malloc(base64_len);

                    if (base64_buffer) {
                        size_t output_len = 0;
                        int ret = mbedtls_base64_encode(base64_buffer, base64_len, &output_len, fb->buf, fb->len);
                        if (ret == 0) {
                            base64_buffer[output_len] = '\0';
                            String base64_str = String((char*)base64_buffer);
                            Firebase.setString(fbdo, "/camara/stream", base64_str);
                        }
                        free(base64_buffer);
                    }
                    esp_camera_fb_return(fb);
                } else {
                    Firebase.setString(fbdo, "/camara/stream", "");
                }
            }
        }

        // ==========================================
        // PARTE C: CONTROL DE FLASH LED
        // ==========================================
        if (Firebase.getInt(fbdo, "/camara/flash")) {
            if (fbdo.intData() == 1) {
                digitalWrite(LED_GPIO_NUM, HIGH);
            } else {
                digitalWrite(LED_GPIO_NUM, LOW);
            }
        }
    }
    delay(1000);
}