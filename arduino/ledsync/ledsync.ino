// Adalight-compatible receiver. Requires the FastLED library.
#include <FastLED.h>

#define NUM_LEDS 94      // must equal Config.TOTAL in the app
#define DATA_PIN 6
CRGB leds[NUM_LEDS];

byte rd() { while (!Serial.available()) {} return Serial.read(); }

void setup() {
  FastLED.addLeds<WS2812B, DATA_PIN, GRB>(leds, NUM_LEDS);
  FastLED.clear(); FastLED.show();
  Serial.begin(115200);
}

void loop() {
  if (rd() != 'A') return;
  if (rd() != 'd') return;
  if (rd() != 'a') return;
  byte hi = rd(), lo = rd(), chk = rd();
  if (chk != (hi ^ lo ^ 0x55)) return;
  if ((((uint16_t)hi << 8) | lo) + 1 != NUM_LEDS) return;
  Serial.readBytes((uint8_t*)leds, NUM_LEDS * 3);
  FastLED.show();
}
