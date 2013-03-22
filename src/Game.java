/**
 * Comment free (yay) source code for Left 4k Dead by Markus Persson
 * Please don't reuse any of this code in other projects.
 * http://www.mojang.com/notch/j4k/l4kd/
 */
import java.awt.AWTEvent;
import java.awt.FlowLayout;
import java.awt.Frame;
import java.awt.Graphics;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.WindowEvent;
import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.util.Random;

public class Game extends Frame {
  private static final long serialVersionUID = 2099860140043826270L;

  private static final int ROOM_COUNT = 70;

  private static final int PIXEL_ZOMBIE_SKIN = 0xa0ff90;
  private static final int PIXEL_SKIN = 0xFF9993;

  private static final int PIXEL_NORMAL_WALL = 0xFF8052;
  private static final int PIXEL_OUTER_WALL = 0xFFFEFE;
  private static final int PIXEL_INNER_WALL = 0xFFFFFF;
  private static final int PIXEL_MASK_WALL = 0xff0000;
  private static final int PIXEL_MONSTER_HEAD = 0xFFFFFE;
  private static final int PIXEL_MASK_END_ROOM = 0xff0000;

  private boolean[] k = new boolean[32767];
  private int mouseEvent;
  private BufferedImage image;
  private Graphics ogr;
  private Random random;
  private int[] pixels;
  private int[] sprites;
  private int[] maps;
  private int score;
  private int hurtTime;
  private int bonusTime;
  private int xWin0;
  private int yWin0;
  private int xWin1;
  private int yWin1;
  private int level;
  private int shootDelay;
  private int rushTime;
  private int damage;
  private int ammo;
  private int clips;
  private boolean gameStarted;
  private Graphics sg;

  private static final int MDO_X = 0;
  private static final int MDO_Y = 1;
  private static final int MDO_DIRECTION = 2;
  private static final int MDO_SPRITE_FRAME = 3;
  private static final int MDO_UNKNOWN_8 = 8;
  private static final int MDO_RAGE_LEVEL = 9;
  private static final int MDO_DAMAGE_TAKEN = 10;
  private static final int MDO_ACTIVITY_LEVEL = 11;
  private static final int MDO_SAVED_MAP_PIXEL = 15;

  public static void main(String[] args) {
    Game game = new Game();
    game.setSize(480, 480);
    game.setVisible(true);
    game.setLayout(new FlowLayout());
    game.run();
  }

  public void windowClosing(WindowEvent e) {
    dispose();
    System.exit(0);
  }

  public void windowOpened(WindowEvent e) {
  }

  public void windowIconified(WindowEvent e) {
  }

  public void windowClosed(WindowEvent e) {
  }

  public void windowDeiconified(WindowEvent e) {
  }

  public void windowActivated(WindowEvent e) {
  }

  public void windowDeactivated(WindowEvent e) {
  }

  public Game() {
    enableEvents(AWTEvent.KEY_EVENT_MASK | AWTEvent.MOUSE_EVENT_MASK
        | AWTEvent.MOUSE_MOTION_EVENT_MASK);
    image = new BufferedImage(240, 240, BufferedImage.TYPE_INT_RGB);
    ogr = image.getGraphics();
    random = new Random();
    pixels = ((DataBufferInt) image.getRaster().getDataBuffer()).getData();
    sprites = new int[18 * 4 * 16 * 12 * 12];
  }

  public void run() {
    generateSprites();

    while (true)
      doGame();
  }

  private void doGame() {
    score = 0;
    hurtTime = 0;
    bonusTime = 0;
    xWin0 = 0;
    yWin0 = 0;
    xWin1 = 0;
    yWin1 = 0;

    while (true) {
      System.out.println("Playing full game...");
      playFullGame();
    }
  }

  private void playFullGame() {
    gameStarted = false;
    level = 0;
    shootDelay = 0;
    rushTime = 150;
    damage = 20;
    ammo = 20;
    clips = 20;

    playLevels();
  }

  private void playLevels() {
    while (true) {
      int tick = 0;
      level++;
      System.out.println("Playing level " + level + "...");
      int[] monsterData = new int[320 * 16];
      generateLevel(monsterData);

      long lastTime = System.nanoTime();

      int[] lightmap = new int[240 * 240];
      int[] brightness = generateBrightness();

      double playerDir = 0;

      sg = getGraphics();
      random = new Random();
      while (true) {
        if (gameStarted) {
          tick++;
          rushTime++;

          if (rushTime >= 150) {
            rushTime = -random.nextInt(2000);
          }
          // Move player:
          int mouse = mouseEvent;
          playerDir = Math.atan2(mouse / 240 - 120, mouse % 240 - 120);

          double shootDir = playerDir
              + (random.nextInt(100) - random.nextInt(100)) / 100.0 * 0.2;
          double cos = Math.cos(-shootDir);
          double sin = Math.sin(-shootDir);

          int xCam = monsterData[MDO_X];
          int yCam = monsterData[MDO_Y];

          generateLightmap(tick, lightmap, brightness, playerDir, xCam, yCam);
          drawMapView(xCam, yCam);

          int closestHitDist = calculateClosestHitDistance(cos, sin, xCam, yCam);

          processMonsters(tick, monsterData, lightmap, playerDir, cos, sin,
              xCam, yCam, closestHitDist);

          if (damage >= 220) {
            k[1] = false;
            hurtTime = 255;
            return;
          }
          if (k[KeyEvent.VK_R] && ammo > 20 && clips < 220) {
            shootDelay = 30;
            ammo = 20;
            clips += 10;
          }

          if (xCam > xWin0 && xCam < xWin1 && yCam > yWin0 && yCam < yWin1) {
            return;
          }
        }

        bonusTime = bonusTime * 8 / 9;
        hurtTime /= 2;

        drawNoiseAndHUD(lightmap);

        ogr.drawString("" + score, 4, 232);
        if (!gameStarted) {
          ogr.drawString("Left 4k Dead", 80, 70);
          if (k[1] && hurtTime == 0) {
            score = 0;
            gameStarted = true;
            k[1] = false;
          }
        } else if (tick < 60) {
          ogr.drawString("Level " + level, 90, 70);
        }

        sg.drawImage(image, 0, 0, 480, 480, 0, 0, 240, 240, null);
        do {
          Thread.yield();
        } while (System.nanoTime() - lastTime < 0);
        if (!isActive())
          return;

        lastTime += (1000000000 / 30);
      }
    }
  }

  private int calculateClosestHitDistance(double cos, double sin, int xCam,
      int yCam) {
    int closestHitDist = 0;
    for (int j = 0; j < 250; j++) {
      int xm = xCam + (int) (cos * j / 2);
      int ym = yCam - (int) (sin * j / 2);

      // 0xffffff is the color of character clothes.
      if (maps[(xm + ym * 1024) & (1024 * 1024 - 1)] == 0xffffff)
        break;
      closestHitDist = j / 2;
    }
    return closestHitDist;
  }

  private void drawMapView(int xCam, int yCam) {
    for (int y = 0; y < 240; y++) {
      int xm = xCam - 120;
      int ym = y + yCam - 120;
      for (int x = 0; x < 240; x++) {
        pixels[x + y * 240] = maps[(xm + x + ym * 1024) & (1024 * 1024 - 1)];
      }
    }
  }

  private void generateLightmap(int tick, int[] lightmap, int[] brightness,
      double playerDir, int xCam, int yCam) {
    for (int i = 0; i < 960; i++) {
      // Calculate a point along the outer wall of the view.
      int xt = i % 240 - 120;
      int yt = (i / 240 % 2) * 239 - 120;

      if (i >= 480) {
        int tmp = xt;
        xt = yt;
        yt = tmp;
      }

      // Figure out how far the current beam is from the player's view.
      // In radians, not degrees, but same idea -- if the player is looking
      // 180 degrees south, and this beam is pointing 270 degrees west,
      // then the answer is 90 degrees (in radians). This is for creating a
      // flashlight effect in front of the player.
      //
      // Clamp to a circle (2 x pi).
      double dd = Math.atan2(yt, xt) - playerDir;
      if (dd < -Math.PI)
        dd += Math.PI * 2;
      if (dd >= Math.PI)
        dd -= Math.PI * 2;

      // This calculation is weird because of the 1- and the *255. It seems
      // arbitrary. Maybe it is. brr is probably supposed to stand for
      // something like "brightness times radius squared."
      int brr = (int) ((1 - dd * dd) * 255);

      int dist = 120;
      if (brr < 0) {
        // Cut off the flashlight past a certain angle, but for better
        // playability leave a small halo going all the way around the player.
        brr = 0;
        dist = 32;
      }
      // At the very start of the level, fade in the light gradually.
      if (tick < 60)
        brr = brr * tick / 60;

      int j = 0;
      for (; j < dist; j++) {
        // Loop through the beam's pixels one fraction of the total distance
        // each iteration. This is very slightly inefficient because in some
        // cases we'll calculate the same pixel twice.
        int xx = xt * j / 120 + 120;
        int yy = yt * j / 120 + 120;
        int xm = xx + xCam - 120;
        int ym = yy + yCam - 120;

        // Stop the light if it hits a wall.
        if (maps[(xm + ym * 1024) & (1024 * 1024 - 1)] == 0xffffff)
          break;

        // Do an approximate distance calculation. I'm not sure why this
        // couldn't have been built into the brightness table, which would let
        // us easily index using j.
        int xd = (xx - 120) * 256 / 120;
        int yd = (yy - 120) * 256 / 120;
        int ddd = (xd * xd + yd * yd) / 256;
        int br = brightness[ddd] * brr / 255;

        // Draw the halo around the player.
        if (ddd < 16) {
          int tmp = 128 * (16 - ddd) / 16;
          br = br + tmp * (255 - br) / 255;
        }

        // Fill in the lightmap entry.
        lightmap[xx + yy * 240] = br;
      }
    }
  }

  private void processMonsters(int tick, int[] monsterData, int[] lightmap,
      double playerDir, double cos, double sin, int xCam, int yCam,
      int closestHitDist) {
    {
      boolean shoot = shootDelay-- < 0 && k[1];
      int closestHit = 0;

      nextMonster: for (int monsterIndex = 0; monsterIndex < 256 + 16; monsterIndex++) {
        int xPos = monsterData[monsterIndex * 16 + MDO_X];
        int yPos = monsterData[monsterIndex * 16 + MDO_Y];

        // Is this monster inactive?
        if (monsterData[monsterIndex * 16 + MDO_ACTIVITY_LEVEL] == 0) {
          // Yes. Try to activate it.

          // Pick a random spot to put it.
          xPos = (random.nextInt(62) + 1) * 16 + 8;
          yPos = (random.nextInt(62) + 1) * 16 + 8;

          // How close is it to the player?
          int xd = xCam - xPos;
          int yd = yCam - yPos;
          if (xd * xd + yd * yd < 180 * 180) {
            // Too close. Not fair. So put the monster inside a wall. I don't
            // understand why this isn't just a continue;
            xPos = 1;
            yPos = 1;
          }

          // Are all these true?
          // 1. The monster is not on a wall or other monster, AND
          // 2. Any of these is true: a. It's an early-numbered monster, OR b.
          // It's rush time, OR c. It's the first tick of the game and it's one
          // of the last 16 monsters.
          if (maps[xPos + yPos * 1024] < PIXEL_MONSTER_HEAD
              && (monsterIndex <= 128 || rushTime > 0 || (monsterIndex > 255 && tick == 1))) {
            placeNewMonster(monsterData, monsterIndex, xPos, yPos);
          } else {
            // Don't activate this monster. Give it another shot next time
            // around.
            continue;
          }
        } else {
          // Calculate distance from player.
          int xd = xPos - xCam;
          int yd = yPos - yCam;

          // One of the special monsters?
          if (monsterIndex >= 255) {
            // Close to the player?
            if (xd * xd + yd * yd < 8 * 8) {
              // Yes. Kill it.

              // Replace the map pixel.
              maps[xPos + yPos * 1024] = monsterData[monsterIndex * 16
                  + MDO_SAVED_MAP_PIXEL];
              // Mark monster inactive.
              monsterData[monsterIndex * 16 + MDO_ACTIVITY_LEVEL] = 0;
              bonusTime = 120;

              // 50-50 chance of resetting damage or giving ammo.
              if ((monsterIndex & 1) == 0) {
                damage = 20;
              } else {
                clips = 20;
              }
              continue;
            }
          } else if (xd * xd + yd * yd > 340 * 340) {
            // Not a special monster. If it wandered too far from the player,
            // or more likely the player wandered too far from it, kill it.
            // Basically, this keeps the player reasonably surrounded with
            // monsters waiting to come to life without wasting too many
            // resources on idle ones.
            maps[xPos + yPos * 1024] = monsterData[monsterIndex * 16
                + MDO_SAVED_MAP_PIXEL];
            monsterData[monsterIndex * 16 + MDO_ACTIVITY_LEVEL] = 0;
            continue;
          }
        }

        // Monster is active. Calculate position relative to player.
        int xm = xPos - xCam + 120;
        int ym = monsterData[monsterIndex * 16 + MDO_Y] - yCam + 120;

        // Get monster's direction. This is just for figuring out which sprite
        // to draw.
        int d = monsterData[monsterIndex * 16 + MDO_DIRECTION];
        if (monsterIndex == 0) {
          // or if this is the player, convert radian direction.
          d = (((int) (playerDir / (Math.PI * 2) * 16 + 4.5 + 16)) & 15);
        }

        d += ((monsterData[monsterIndex * 16 + MDO_SPRITE_FRAME] / 4) & 3) * 16;

        // If non-special monster, convert to actual sprite pixel offset.
        int p = (0 * 16 + d) * 144;
        if (monsterIndex > 0) {
          p += ((monsterIndex & 15) + 1) * 144 * 16 * 4;
        }

        // Special non-player monster: cycle through special sprite, either
        // red or yellow, spinning.
        if (monsterIndex > 255) {
          p = (17 * 4 * 16 + ((monsterIndex & 1) * 16 + (tick & 15))) * 144;
        }

        // Render the monster.
        for (int y = ym - 6; y < ym + 6; y++) {
          for (int x = xm - 6; x < xm + 6; x++) {
            int c = sprites[p++];
            if (c > 0 && x >= 0 && y >= 0 && x < 240 && y < 240) {
              pixels[x + y * 240] = c;
            }
          }
        }

        boolean moved = false;

        // Has this monster just taken some damage?
        if (monsterData[monsterIndex * 16 + MDO_DAMAGE_TAKEN] > 0) {
          processMonsterDamage(monsterData, playerDir, monsterIndex, xPos, yPos);
          continue nextMonster;
        }

        int xPlayerDist = xCam - xPos;
        int yPlayerDist = yCam - yPos;

        // Not special monster?
        if (monsterIndex <= 255) {
          // Calculate distance to player.
          double rx = -(cos * xPlayerDist - sin * yPlayerDist);
          double ry = cos * yPlayerDist + sin * xPlayerDist;

          // Is this monster near the player?
          if (rx > -6 && rx < 6 && ry > -6 && ry < 6 && monsterIndex > 0) {
            // Yep. Hurt.
            damage++;
            hurtTime += 20;
          }

          // If the monster is nearby, get agitated.
          if (rx > -32 && rx < 220 && ry > -32 && ry < 32
              && random.nextInt(10) == 0) {
            monsterData[monsterIndex * 16 + MDO_RAGE_LEVEL]++;
          }

          // Mark which monster so far is closest to the player.
          if (rx > 0 && rx < closestHitDist && ry > -8 && ry < 8) {
            closestHitDist = (int) (rx);
            closestHit = monsterIndex;
          }

          dirLoop: for (int i = 0; i < 2; i++) {
            int xa = 0;
            int ya = 0;
            xPos = monsterData[monsterIndex * 16 + MDO_X];
            yPos = monsterData[monsterIndex * 16 + MDO_Y];

            if (monsterIndex == 0) {
              // Move the player according to keyboard state.
              if (k[KeyEvent.VK_A])
                xa--;
              if (k[KeyEvent.VK_D])
                xa++;
              if (k[KeyEvent.VK_W])
                ya--;
              if (k[KeyEvent.VK_S])
                ya++;
            } else {
              // Not agitated enough. Don't do anything.
              if (monsterData[monsterIndex * 16 + MDO_RAGE_LEVEL] < 8)
                continue nextMonster;

              // Unsure. Seems to be some kind of wandering algorithm.
              if (monsterData[monsterIndex * 16 + MDO_UNKNOWN_8] != 12) {
                xPlayerDist = (monsterData[monsterIndex * 16 + MDO_UNKNOWN_8]) % 5 - 2;
                yPlayerDist = (monsterData[monsterIndex * 16 + MDO_UNKNOWN_8]) / 5 - 2;
                if (random.nextInt(10) == 0) {
                  monsterData[monsterIndex * 16 + MDO_UNKNOWN_8] = 12;
                }
              }

              // Move generally toward the player.
              double xxd = Math.sqrt(xPlayerDist * xPlayerDist);
              double yyd = Math.sqrt(yPlayerDist * yPlayerDist);
              if (random.nextInt(1024) / 1024.0 < yyd / xxd) {
                if (yPlayerDist < 0)
                  ya--;
                if (yPlayerDist > 0)
                  ya++;
              }
              if (random.nextInt(1024) / 1024.0 < xxd / yyd) {
                if (xPlayerDist < 0)
                  xa--;
                if (xPlayerDist > 0)
                  xa++;
              }

              // Mark that the monster moved so we can update pixels later.
              moved = true;

              // Pick the right sprite frame depending on direction.
              double dir = Math.atan2(yPlayerDist, xPlayerDist);
              monsterData[monsterIndex * 16 + MDO_DIRECTION] = (((int) (dir
                  / (Math.PI * 2) * 16 + 4.5 + 16)) & 15);
            }

            // I think this is a way to move fast but not go through walls.
            // Start by moving a small amount, test for wall hit, if successful
            // try moving more.
            ya *= i;
            xa *= 1 - i;

            // Did the monster move?
            if (xa != 0 || ya != 0) {
              // Restore the map pixel.
              maps[xPos + yPos * 1024] = monsterData[monsterIndex * 16
                  + MDO_SAVED_MAP_PIXEL];

              // Did the monster bonk into a wall?
              for (int xx = xPos + xa - 3; xx <= xPos + xa + 3; xx++) {
                for (int yy = yPos + ya - 3; yy <= yPos + ya + 3; yy++) {
                  if (maps[xx + yy * 1024] >= 0xfffffe) {
                    // Yes. Put back the pixel.
                    maps[xPos + yPos * 1024] = 0xfffffe;
                    // Try wandering in a different direction.
                    monsterData[monsterIndex * 16 + MDO_UNKNOWN_8] = random
                        .nextInt(25);
                    // And then move all over again.
                    continue dirLoop;
                  }
                }
              }

              // Move the monster.
              moved = true;
              monsterData[monsterIndex * 16 + MDO_X] += xa;
              monsterData[monsterIndex * 16 + MDO_Y] += ya;

              // Save the pixel.
              monsterData[monsterIndex * 16 + MDO_SAVED_MAP_PIXEL] = maps[(xPos + xa)
                  + (yPos + ya) * 1024];

              // Draw the monster's head.
              maps[(xPos + xa) + (yPos + ya) * 1024] = 0xfffffe;
            }
          }
          if (moved) {
            // Shuffle to next frame in sprite.
            monsterData[monsterIndex * 16 + MDO_SPRITE_FRAME]++;
          }
        }
      }

      // Did the player press the fire button?
      if (shoot) {
        // Is the ammo used up?
        if (ammo >= 220) {
          // Yes. Longer delay.
          shootDelay = 2;
          // Require trigger release.
          k[1] = false;
        } else {
          // Fast fire.
          shootDelay = 1;
          // Use up bullets.
          ammo += 4;
        }

        // Whoever's closest gets hit. But how do we know direction was right?
        if (closestHit > 0) {
          monsterData[closestHit * 16 + MDO_DAMAGE_TAKEN] = 1;
          monsterData[closestHit * 16 + MDO_RAGE_LEVEL] = 127;
        }

        // Draw the trace.
        int glow = 0;
        for (int j = closestHitDist; j >= 0; j--) {
          // Calculate pixel position.
          int xm = +(int) (cos * j) + 120;
          int ym = -(int) (sin * j) + 120;

          // Are we still within the view?
          if (xm > 0 && ym > 0 && xm < 240 && ym < 240) {

            // Every so often, draw a white dot and renew the glow. This gives a
            // cool randomized effect that looks like spitting sparks.
            if (random.nextInt(20) == 0 || j == closestHitDist) {
              pixels[xm + ym * 240] = 0xffffff;
              glow = 200;
            }
            
            // Either way, brighten up the path according to the current glow.
            lightmap[xm + ym * 240] += glow * (255 - lightmap[xm + ym * 240])
                / 255;
          }
          
          // Fade the glow.
          glow = glow * 20 / 21;
        }

        // Did the bullet hit within view?
        if (closestHitDist < 120) {
          closestHitDist -= 3;
          int xx = (int) (120 + cos * closestHitDist);
          int yy = (int) (120 - sin * closestHitDist);

          // Make a great big flash where the bullet hit.
          for (int x = -12; x <= 12; x++) {
            for (int y = -12; y <= 12; y++) {
              int xd = xx + x;
              int yd = yy + y;
              if (xd >= 0 && yd >= 0 && xd < 240 && yd < 240) {
                lightmap[xd + yd * 240] += 2000 / (x * x + y * y + 10)
                    * (255 - lightmap[xd + yd * 240]) / 255;
              }
            }
          }

          // And draw a bunch of debris.
          for (int i = 0; i < 10; i++) {
            double pow = random.nextInt(100) * random.nextInt(100) * 8.0
                / 10000;
            double dir = (random.nextInt(100) - random.nextInt(100)) / 100.0;
            int xd = (int) (xx - Math.cos(playerDir + dir) * pow)
                + random.nextInt(4) - random.nextInt(4);
            int yd = (int) (yy - Math.sin(playerDir + dir) * pow)
                + random.nextInt(4) - random.nextInt(4);
            if (xd >= 0 && yd >= 0 && xd < 240 && yd < 240) {
              if (closestHit > 0) {
                pixels[xd + yd * 240] = 0xff0000;
              } else {
                pixels[xd + yd * 240] = 0xcacaca;
              }
            }
          }
        }
      }
    }
  }

  private void placeNewMonster(int[] monsterData, int m, int xPos, int yPos) {
    // Yes. Place the monster here.
    monsterData[m * 16 + MDO_X] = xPos;
    monsterData[m * 16 + MDO_Y] = yPos;

    // Remember this map pixel.
    monsterData[m * 16 + MDO_SAVED_MAP_PIXEL] = maps[xPos + yPos * 1024];

    // Mark the map as having a monster here.
    maps[xPos + yPos * 1024] = PIXEL_MONSTER_HEAD;

    // Mark monster as idle or attacking.
    monsterData[m * 16 + MDO_RAGE_LEVEL] = (rushTime > 0 || random.nextInt(3) == 0) ? 127
        : 0;

    // Mark monster active.
    monsterData[m * 16 + MDO_ACTIVITY_LEVEL] = 1;

    // Distribute the monsters' initial direction.
    monsterData[m * 16 + MDO_DIRECTION] = m & 15;
  }

  private void processMonsterDamage(int[] monsterData, double playerDir, int m,
      int xPos, int yPos) {
    // Yes.
    // Add to monster's cumulative damage and reset temp damage.
    monsterData[m * 16 + MDO_ACTIVITY_LEVEL] += random.nextInt(3) + 1;
    monsterData[m * 16 + MDO_DAMAGE_TAKEN] = 0;

    double rot = 0.25; // How far around the blood spreads, radians
    int amount = 8; // How much blood
    double poww = 32; // How far to spread the blood

    // Is this monster sufficiently messed up to die?
    if (monsterData[m * 16 + MDO_ACTIVITY_LEVEL] >= 2 + level) {
      rot = Math.PI * 2; // All the way around
      amount = 60; // lots of blood
      poww = 16;
      maps[(xPos) + (yPos) * 1024] = 0xa00000; // Red
      monsterData[m * 16 + MDO_ACTIVITY_LEVEL] = 0; // Kill monster
      score += level; // Increase player score
    }

    // Draw blood.
    for (int i = 0; i < amount; i++) {
      double pow = (random.nextInt(100) * random.nextInt(100)) * poww / 10000
          + 4;
      double dir = (random.nextInt(100) - random.nextInt(100)) / 100.0 * rot;
      double xdd = (Math.cos(playerDir + dir) * pow) + random.nextInt(4)
          - random.nextInt(4);
      double ydd = (Math.sin(playerDir + dir) * pow) + random.nextInt(4)
          - random.nextInt(4);
      int col = (random.nextInt(128) + 120);
      bloodLoop: for (int j = 2; j < pow; j++) {
        int xd = (int) (xPos + xdd * j / pow);
        int yd = (int) (yPos + ydd * j / pow);
        int pp = ((xd) + (yd) * 1024) & (1024 * 1024 - 1);

        // If the blood encounters a wall, stop spraying.
        if (maps[pp] >= 0xff0000)
          break bloodLoop;

        // Occasionally splat some blood and darken it.
        if (random.nextInt(2) != 0) {
          maps[pp] = col << 16;
          col = col * 8 / 9;
        }
      }
    }
  }

  private void drawNoiseAndHUD(int[] lightmap) {
    for (int y = 0; y < 240; y++) {
      for (int x = 0; x < 240; x++) {
        int noise = random.nextInt(16) * random.nextInt(16) / 16;
        if (!gameStarted)
          noise *= 4;

        int c = pixels[x + y * 240];
        int l = lightmap[x + y * 240];
        lightmap[x + y * 240] = 0;
        int r = ((c >> 16) & 0xff) * l / 255 + noise;
        int g = ((c >> 8) & 0xff) * l / 255 + noise;
        int b = ((c) & 0xff) * l / 255 + noise;

        r = r * (255 - hurtTime) / 255 + hurtTime;
        g = g * (255 - bonusTime) / 255 + bonusTime;
        pixels[x + y * 240] = r << 16 | g << 8 | b;
      }
      if (y % 2 == 0 && (y >= damage && y < 220)) {
        for (int x = 232; x < 238; x++) {
          pixels[y * 240 + x] = 0x800000;
        }
      }
      if (y % 2 == 0 && (y >= ammo && y < 220)) {
        for (int x = 224; x < 230; x++) {
          pixels[y * 240 + x] = 0x808000;
        }
      }
      if (y % 10 < 9 && (y >= clips && y < 220)) {
        for (int x = 221; x < 222; x++) {
          pixels[y * 240 + 221] = 0xffff00;
        }
      }
    }
  }

  private int[] generateBrightness() {
    int[] brightness = new int[512];

    double offs = 30;
    for (int i = 0; i < 512; i++) {
      brightness[i] = (int) (255.0 * offs / (i + offs));
      if (i < 4)
        brightness[i] = brightness[i] * i / 4;
    }
    return brightness;
  }

  private void generateLevel(int[] monsterData) {
    maps = new int[1024 * 1024];

    // Make the levels random but repeatable.
    random = new Random(4329 + level);

    // Draw the floor of the level with an uneven green color.
    // Put a wall around the perimeter.
    int i = 0;
    for (int y = 0; y < 1024; y++) {
      for (int x = 0; x < 1024; x++) {
        int br = random.nextInt(32) + 112;
        maps[i] = (br / 3) << 16 | (br) << 8;
        if (x < 4 || y < 4 || x >= 1020 || y >= 1020) {
          maps[i] = PIXEL_OUTER_WALL;
        }
        i++;
      }
    }

    // Create 70 rooms. Put the player in the 69th, and make the 70th red.
    for (i = 0; i < ROOM_COUNT; i++) {
      // Create a room that's possibly as big as the level, whose coordinates
      // are clamped to the nearest multiple of 16.
      int w = random.nextInt(8) + 2;
      int h = random.nextInt(8) + 2;
      int xm = random.nextInt(64 - w - 2) + 1;
      int ym = random.nextInt(64 - h - 2) + 1;

      w *= 16;
      h *= 16;

      w += 5;
      h += 5;
      xm *= 16;
      ym *= 16;

      // Place the player (monsterData[0-15]) in the center of the
      // second-to-last room.
      if (i == ROOM_COUNT - 2) {
        monsterData[MDO_X] = xm + w / 2;
        monsterData[MDO_Y] = ym + h / 2;
        monsterData[MDO_SAVED_MAP_PIXEL] = 0x808080;
        monsterData[MDO_ACTIVITY_LEVEL] = 1;
      }

      // Create a window around the current room coordinates. Why is the first
      // one a width and the second a position?
      xWin0 = xm + 5;
      yWin0 = ym + 5;
      xWin1 = xm + w - 5;
      yWin1 = ym + h - 5;

      for (int y = ym; y < ym + h; y++) {
        for (int x = xm; x < xm + w; x++) {

          // This seems to calculate the thickness of the wall.
          int d = x - xm;
          if (xm + w - x - 1 < d)
            d = xm + w - x - 1;
          if (y - ym < d)
            d = y - ym;
          if (ym + h - y - 1 < d)
            d = ym + h - y - 1;

          // Are we inside the wall, and thus in the room?
          if (d > 4) {
            // Yes, we are. Draw the floor.

            // Vary the color of the floor.
            int br = random.nextInt(16) + 112;

            // Floor diagonal
            if (((x + y) & 3) == 0) {
              br += 16;
            }

            // Grayish concrete floor
            maps[x + y * 1024] = (br * 3 / 3) << 16 | (br * 4 / 4) << 8
                | (br * 4 / 4);
          } else {
            // No, we're not. Draw the wall.
            // Orange wall border
            maps[x + y * 1024] = PIXEL_NORMAL_WALL;
          }

          if (i == ROOM_COUNT - 1) {
            // Give this room a red tint.
            maps[x + y * 1024] &= PIXEL_MASK_END_ROOM;
          }
        }
      }

      // Put two exits in the room.
      for (int j = 0; j < 2; j++) {
        int xGap = random.nextInt(w - 24) + xm + 5;
        int yGap = random.nextInt(h - 24) + ym + 5;
        int ww = 5;
        int hh = 5;

        xGap = xGap / 16 * 16 + 5;
        yGap = yGap / 16 * 16 + 5;
        if (random.nextInt(2) == 0) {
          xGap = xm + (w - 5) * random.nextInt(2);
          hh = 11;
        } else {
          ww = 11;
          yGap = ym + (h - 5) * random.nextInt(2);
        }
        for (int y = yGap; y < yGap + hh; y++) {
          for (int x = xGap; x < xGap + ww; x++) {
            // A slightly darker color represents the exit.
            int br = random.nextInt(32) + 112 - 64;
            maps[x + y * 1024] = (br * 3 / 3) << 16 | (br * 4 / 4) << 8
                | (br * 4 / 4);
          }
        }
      }
    }

    // Paint the inside of each wall white. This is for wall-collision
    // detection.
    for (int y = 1; y < 1024 - 1; y++) {
      inloop: for (int x = 1; x < 1024 - 1; x++) {
        for (int xx = x - 1; xx <= x + 1; xx++) {
          for (int yy = y - 1; yy <= y + 1; yy++) {
            if (maps[xx + yy * 1024] < PIXEL_MASK_WALL) {
              continue inloop;
            }
          }
        }
        maps[x + y * 1024] = PIXEL_INNER_WALL;
      }
    }
  }

  /**
   * Generate a bunch of top-down sprites using surprisingly compact code.
   */
  private void generateSprites() {
    int pix = 0;
    for (int i = 0; i < 18; i++) {
      int skin = PIXEL_SKIN;
      int clothes = 0xFFffff;

      if (i > 0) {
        skin = PIXEL_ZOMBIE_SKIN;
        clothes = (random.nextInt(0x1000000) & 0x7f7f7f);
      }
      for (int t = 0; t < 4; t++) {
        for (int d = 0; d < 16; d++) {
          double dir = d * Math.PI * 2 / 16.0;

          if (t == 1)
            dir += 0.5 * Math.PI * 2 / 16.0;
          if (t == 3)
            dir -= 0.5 * Math.PI * 2 / 16.0;

          // if (i == 17)
          // {
          // dir = d * Math.PI * 2 / 64;
          // }

          double cos = Math.cos(dir);
          double sin = Math.sin(dir);

          for (int y = 0; y < 12; y++) {
            int col = 0x000000;
            for (int x = 0; x < 12; x++) {
              int xPix = (int) (cos * (x - 6) + sin * (y - 6) + 6.5);
              int yPix = (int) (cos * (y - 6) - sin * (x - 6) + 6.5);

              if (i == 17) {
                if (xPix > 3 && xPix < 9 && yPix > 3 && yPix < 9) {
                  col = 0xff0000 + (t & 1) * 0xff00;
                }
              } else {
                if (t == 1 && xPix > 1 && xPix < 4 && yPix > 3 && yPix < 8)
                  col = skin;
                if (t == 3 && xPix > 8 && xPix < 11 && yPix > 3 && yPix < 8)
                  col = skin;

                if (xPix > 1 && xPix < 11 && yPix > 5 && yPix < 8) {
                  col = clothes;
                }
                if (xPix > 4 && xPix < 8 && yPix > 4 && yPix < 8) {
                  col = skin;
                }
              }
              sprites[pix++] = col;
              if (col > 1) {
                col = 1;
              } else {
                col = 0;
              }
            }
          }
        }
      }
    }
  }

  /**
   * Scan key event and turn into a bitmap.
   */
  public void processEvent(AWTEvent e) {
    boolean down = false;
    switch (e.getID()) {
    case KeyEvent.KEY_PRESSED:
      down = true;
    case KeyEvent.KEY_RELEASED:
      k[((KeyEvent) e).getKeyCode()] = down;
      break;
    case MouseEvent.MOUSE_PRESSED:
      down = true;
    case MouseEvent.MOUSE_RELEASED:
      k[((MouseEvent) e).getButton()] = down;
    case MouseEvent.MOUSE_MOVED:
    case MouseEvent.MOUSE_DRAGGED:
      mouseEvent = ((MouseEvent) e).getX() / 2 + ((MouseEvent) e).getY() / 2
          * 240;
    }
  }
}
