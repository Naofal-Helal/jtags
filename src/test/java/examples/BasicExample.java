package examples;

public class BasicExample {

  private boolean method1() {
    return true;
  }

  public static void method2(
      String string1,
      String string2,
      String string3,
      String string4,
      String string5,
      String string6) {

    Object innerObject =
        new Object() {
          void method2() {}
        };

    class InnerClass {
      void method3() {}

      private void method4() {}
    }
  }
}
