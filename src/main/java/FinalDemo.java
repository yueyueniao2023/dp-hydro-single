//import java.util.ArrayList;
//import java.util.List;
//
//public class FinalDemo {
//    // 1. 基本类型final：值不可改
//    public final int NUM = 100;
//    // 2. 引用类型final：地址不可改，内容可改
//    public final List<String> LIST = new ArrayList<>();
//    // 3. 静态final：必须在声明/静态代码块初始化
//    public final static double PI;
//
//    static {
//        PI = 3.1415926; // 静态代码块初始化
//    }
//
//    public static void main(String[] args) {
//        FinalDemo demo = new FinalDemo();
//        // demo.NUM = 200; // 编译报错：final变量不可重新赋值
//
//        demo.LIST.add("Java"); // 合法：集合内容可改
//        System.out.println(demo.LIST); // 输出[Java]
//
//         //demo.LIST = new LinkedList<>(); // 编译报错：引用地址不可改
//
//         //FinalDemo.PI = 3.14; // 编译报错：static final变量不可改
//    }
//}