import java.util.*;
import java.util.concurrent.*;
import java.util.function.Function;

// --- Multi-Agent AI Simulation Framework ---
public class MultiAgentAI {

    // --- Pipeline Stage Interface ---
    public interface Stage<I, O> {
        O process(I input, SharedMemory memory);
    }

    // --- Shared memory between agents ---
    public static class SharedMemory {
        private Map<String, Object> memory = new ConcurrentHashMap<>();
        public void put(String key, Object value) { memory.put(key, value); }
        public Object get(String key) { return memory.get(key); }
        public boolean contains(String key) { return memory.containsKey(key); }
    }

    // --- Agent Interface ---
    public interface Agent {
        String getName();
        String act(String input, SharedMemory shared);
    }

    // --- Abstract Base Agent with pipeline ---
    public static abstract class PipelineAgent implements Agent {
        protected String name;
        protected List<Stage<String, String>> pipeline = new ArrayList<>();
        protected Map<String, Object> memory = new ConcurrentHashMap<>();

        public PipelineAgent(String name) { this.name = name; }

        public void addStage(Stage<String, String> stage) { pipeline.add(stage); }

        public void remember(String key, Object value) { memory.put(key, value); }
        public Object recall(String key) { return memory.get(key); }

        @Override
        public String getName() { return name; }

        @Override
        public String act(String input, SharedMemory shared) {
            String output = input;
            for (Stage<String, String> stage : pipeline) {
                output = stage.process(output, shared);
            }
            remember("last_output", output);
            return output;
        }
    }

    // --- Example Preprocessing Stage ---
    public static class LowercaseStage implements Stage<String, String> {
        @Override
        public String process(String input, SharedMemory memory) {
            return input.toLowerCase();
        }
    }

    // --- Example Reasoning Stage ---
    public static class KeywordStage implements Stage<String, String> {
        private Map<String, String> rules = new HashMap<>();
        public void addRule(String keyword, String response) { rules.put(keyword.toLowerCase(), response); }
        @Override
        public String process(String input, SharedMemory memory) {
            for (String key : rules.keySet()) {
                if (input.contains(key)) return rules.get(key);
            }
            return "No action";
        }
    }

    // --- Example Postprocessing Stage ---
    public static class EchoStage implements Stage<String, String> {
        @Override
        public String process(String input, SharedMemory memory) {
            return "Agent says: " + input;
        }
    }

    // --- Environment managing agents ---
    public static class Environment {
        private List<Agent> agents = new ArrayList<>();
        private SharedMemory shared = new SharedMemory();

        public void addAgent(Agent agent) { agents.add(agent); }

        public void runRound(String stimulus) {
            ExecutorService executor = Executors.newCachedThreadPool();
            List<Future<String>> futures = new ArrayList<>();
            for (Agent agent : agents) {
                futures.add(executor.submit(() -> agent.act(stimulus, shared)));
            }
            for (int i = 0; i < agents.size(); i++) {
                try { System.out.println(agents.get(i).getName() + " -> " + futures.get(i).get()); }
                catch (Exception e) { e.printStackTrace(); }
            }
            executor.shutdown();
        }

        public void runRounds(String[] stimuli) {
            for (String stim : stimuli) {
                System.out.println("\nStimulus: " + stim);
                runRound(stim);
            }
        }

        public SharedMemory getSharedMemory() { return shared; }
    }

    // --- Evaluator ---
    public static class Evaluator {
        public static double evaluateAccuracy(Agent agent, Map<String, String> testSet, SharedMemory shared) {
            int correct = 0;
            for (Map.Entry<String, String> entry : testSet.entrySet()) {
                if (agent.act(entry.getKey(), shared).equals(entry.getValue())) correct++;
            }
            return (double) correct / testSet.size();
        }
    }

    // --- Plugin system ---
    public interface Plugin { void enhance(Agent agent, SharedMemory shared); }
    public static class LoggingPlugin implements Plugin {
        @Override
        public void enhance(Agent agent, SharedMemory shared) {
            System.out.println("Plugin applied to agent: " + agent.getName());
        }
    }

    // --- Example Agent Implementation ---
    public static class MyPipelineAgent extends PipelineAgent {
        public MyPipelineAgent(String name) { super(name); }
    }

    // --- Main ---
    public static void main(String[] args) {
        Environment env = new Environment();

        // Agent 1: Keyword-based
        MyPipelineAgent agent1 = new MyPipelineAgent("KeywordBot");
        KeywordStage reason1 = new KeywordStage();
        reason1.addRule("hello", "Hi there!");
        reason1.addRule("bye", "Goodbye!");
        agent1.addStage(new LowercaseStage());
        agent1.addStage(reason1);
        agent1.addStage(new EchoStage());

        // Agent 2: Echo functional agent
        MyPipelineAgent agent2 = new MyPipelineAgent("EchoBot");
        agent2.addStage(input -> "Echoing: " + input);

        env.addAgent(agent1);
        env.addAgent(agent2);

        String[] stimuli = {"Hello world", "Random message", "Bye now"};
        env.runRounds(stimuli);

        // Evaluation
        Map<String, String> testSet = new HashMap<>();
        testSet.put("hello friend", "Agent says: Hi there!");
        testSet.put("bye everyone", "Agent says: Goodbye!");
        double acc = Evaluator.evaluateAccuracy(agent1, testSet, env.getSharedMemory());
        System.out.println("\nKeywordBot accuracy: " + acc);

        // Plugin
        LoggingPlugin plugin = new LoggingPlugin();
        plugin.enhance(agent1, env.getSharedMemory());
        plugin.enhance(agent2, env.getSharedMemory());

        // Agents can communicate via shared memory
        env.getSharedMemory().put("announcement", "System update available");
        System.out.println("\nShared memory message for agents: " + env.getSharedMemory().get("announcement"));
    }
}
