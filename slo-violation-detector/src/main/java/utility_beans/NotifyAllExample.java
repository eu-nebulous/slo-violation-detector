package utility_beans;

public class NotifyAllExample {
    public static void main(String[] args) {
        SharedObject sharedObject = new SharedObject();

        for (int i = 0; i < 5; i++) {
            Thread thread = new Thread(new MyRunnable(sharedObject));
            thread.start();
        }

        try {
            Thread.sleep(2000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        synchronized (sharedObject) {
            sharedObject.notifyAll();
        }
    }
}

class MyRunnable implements Runnable {
    private final SharedObject sharedObject;

    public MyRunnable(SharedObject sharedObject) {
        this.sharedObject = sharedObject;
    }

    @Override
    public void run() {
        synchronized (sharedObject) {
            try {
                sharedObject.wait();
                System.out.println("Thread " + Thread.currentThread().getId() + " is awake");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

class SharedObject {
    // Shared variable
}

