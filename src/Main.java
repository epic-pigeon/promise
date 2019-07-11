public class Main {
    public static void main(String[] args) {
        int millis = (int) Math.round(Math.random() * 1000) + 1000;
        waitFor(millis).then(result -> System.out.println("waited " + millis));
    }

    private static Promise<Void, InterruptedException> waitFor(int millis) {
        return Promise.Builder.<Void, InterruptedException>instance()
                .setAction((resolver, rejecter) -> {
                    Thread.sleep(millis);
                    resolver.resolve(null);
                })
                .setExecutor(new Promise.AsyncExecutor<>())
                .build();
    }
}
