class Point {
  Point(int x, int y) {
    this.x = x;
    this.y = y;
  }

  int x, y;

  @Override
  public String toString() {
    return "Point [x=" + this.x + ", y=" + this.y + "]";
  }
}