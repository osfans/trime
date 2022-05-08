package com.osfans.trime.ime.keyboard;

import android.media.AudioAttributes;
import android.media.AudioManager;
import android.media.SoundPool;
import android.view.KeyEvent;
import com.osfans.trime.data.AppPrefs;
import com.osfans.trime.util.ConfigGetter;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class Sound {
  private final SoundPool sp; // 声明SoundPool的引用
  private int currStreamId; // 当前正播放的streamId
  private int lastKeycode; // 上次按键的键盘码
  private int[] sound; // 音频文件列表
  private final List<Key> keyset;
  private List<String> files;
  private static Sound self;
  private final boolean enable;
  private int progress;
  private int[] melody;

  private static final AppPrefs appPrefs = AppPrefs.defaultInstance();

  public static Sound get() {
    return self;
  }

  public static Sound get(String soundPackageName) {
    if (self != null) self.sp.release();
    self = new Sound(soundPackageName);
    return self;
  }

  public static boolean isEnable() {
    if (self == null) return false;
    return self.enable;
  }

  public static void resetProgress() {
    if (self != null) {
      if (self.progress > 0) self.progress = 0;
    }
  }

  public Sound(String soundPackageName) {
    AudioAttributes audioAttributes =
        new AudioAttributes.Builder().setLegacyStreamType(AudioManager.STREAM_SYSTEM).build();
    sp = new SoundPool.Builder().setAudioAttributes(audioAttributes).setMaxStreams(3).build();
    keyset = new ArrayList<>();
    melody = new int[1];
    progress = -1;

    Map<String, ?> m = ConfigGetter.loadMap(soundPackageName + ".sound", "");
    if (m != null) {
      String path = appPrefs.getConf().getUserDataDir() + File.separator + "sound" + File.separator;
      if (m.containsKey("folder")) path = path + m.get("folder") + File.separator;

      if (m.containsKey("sound")) {
        files = (List<String>) m.get("sound");
        sound = new int[files.size()];
        int i = 0;
        for (String file : files) {
          sound[i] = sp.load(path + file, 1);
          i++;
        }

        if (m.containsKey("melody")) {
          progress = 0;
          List<String> n = (List<String>) m.get("melody");
          melody = new int[n.size()];
          for (int j = 0; j < n.size(); j++) {
            melody[j] = getSoundIndex(n.get(j));
          }
          enable = true;
          return;
        } else if (m.containsKey("keyset")) {
          List<Map<String, ?>> n = (List<Map<String, ?>>) m.get("keyset");
          for (Map<String, ?> o : n) {
            int max = -1, min = -1;
            boolean inOrder = true;
            int[] sounds = new int[1];
            Object keys;

            if (o.containsKey("inOrder")) {
              Object _inOrder = o.get("inOrder");
              if (_inOrder != null) inOrder = (_inOrder.equals("true"));
            }
            if (o.containsKey("sounds")) {
              List<?> s = (List<?>) o.get("sounds");
              assert s != null;
              if (s.size() > 1) sounds = new int[s.size()];
              for (int j = 0; j < s.size(); j++) {
                sounds[j] = getSoundIndex(s.get(j));
              }
            }
            if (o.containsKey("keys")) {
              keys = o.get("keys");
              if (keys instanceof List) keyset.add(new Key((List<?>) keys, inOrder, sounds));
            } else {
              if (o.containsKey("max")) max = getKeycode(o.get("max"));
              if (o.containsKey("min")) min = getKeycode(o.get("min"));
              keyset.add(new Key(min, max, inOrder, sounds));
            }
          }
          enable = true;
          return;
        }
      }
    }
    enable = false;
  }

  public void play(Integer keycode, Integer volume) {
    if (volume > 0) {
      if (sound.length > 0) {
        float soundVolume = volume / 100f;
        if (progress >= 0) {
          if (progress >= melody.length) progress = 0;
          currStreamId = melody[progress];
          progress++;
        } else if (lastKeycode != keycode) {
          lastKeycode = keycode;
          for (Key key : keyset) {
            currStreamId = key.getSound(keycode);
            if (currStreamId >= 0) {
              sp.play(sound[currStreamId], soundVolume, soundVolume, 0, 0, 1.0f);
              return;
            }
          }
          currStreamId = 0;
        }
        sp.play(sound[currStreamId], soundVolume, soundVolume, 0, 0, 1.0f);
      }
    }
  }

  public static int getKeycode(Object string) {
    String keyName = ((String) string).toUpperCase(Locale.ROOT);
    if (keyName.startsWith("KEYCODE_")) return KeyEvent.keyCodeFromString(keyName);
    else return KeyEvent.keyCodeFromString("KEYCODE_" + keyName);
  }

  public int getSoundIndex(Object obj) {
    String t = (String) obj;
    int k;
    if (t.matches("\\d+")) {
      k = Integer.parseInt(t);
      if (k >= sound.length) return 0;
    } else k = files.indexOf(t);
    return Math.max(k, 0);
  }

  private static class Key {
    private int max, min;
    private boolean inOrder;
    private int[] sounds;
    private List<Integer> keys;

    public Key(int min, int max, boolean inOrder, int[] sound) {
      if (max < min) return;
      this.max = max;
      this.min = min;
      this.inOrder = inOrder;
      this.sounds = sound;
      this.keys = null;
    }

    public Key(List<?> keys, boolean inOrder, int[] sound) {
      this.keys = new ArrayList<>();
      if (keys.size() > 0) {
        int i = 0;
        //                Object key = keys.get(0);
        //                if(key instanceof String)
        {
          for (; i < keys.size(); i++) {
            this.keys.add(getKeycode(keys.get(i)));
          }
        }
        /*
        yaml不能直接识别int，为避免keycode和键名0-9无法区分，暂时不支持keycode参数
        else if(key instanceof Integer){
            for(;i<keys.size();i++){
                 int keyCode =(Integer) keys.get(i);
                    this.keys[i] = keyCode;
            }
        }*/
      }
      this.inOrder = inOrder;
      this.sounds = sound;
    }

    public int getSound(int keycode) {
      if (sounds.length < 1) return -1;

      if (keys != null) {
        int i = keys.indexOf(keycode);
        if (i >= 0) {
          if (!inOrder) i = (int) (Math.random() * sounds.length);
          return sounds[i % sounds.length];
        }
      } else {
        if (keycode >= min && keycode <= max) {
          if (inOrder) return sounds[(keycode - min) % sounds.length];
          int i = (int) (Math.random() * sounds.length);
          return sounds[i % sounds.length];
        }
      }
      return -1;
    }
  }
}
