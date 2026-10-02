package network;

import cascade.Reservoir;
import java.util.*;

/** 有向水系。course 只作说明，汇流计算只读取 edges 和直接上游集合。 */
public final class RiverNetwork {
    public record Node(String id, String course, Reservoir reservoir) {
        public Node {
            if (id == null || id.isBlank() || course == null || course.isBlank() || reservoir == null)
                throw new IllegalArgumentException("水库编号、河流属性及物理参数不能为空");
        }
    }
    public record Edge(String from, String to) { }

    private final Map<String, Node> nodes = new LinkedHashMap<>();
    private final Map<String, List<String>> upstream = new LinkedHashMap<>();
    private final List<String> order;
    private final List<Edge> edges;

    public RiverNetwork(List<Node> reservoirs, List<Edge> connections) {
        if (reservoirs == null || reservoirs.isEmpty() || connections == null)
            throw new IllegalArgumentException("水系不能为空");
        for (Node node : reservoirs) {
            if (node == null || nodes.putIfAbsent(node.id(), node) != null)
                throw new IllegalArgumentException("水库编号重复或为空");
            upstream.put(node.id(), new ArrayList<>());
        }
        Map<String, List<String>> downstream = new LinkedHashMap<>();
        for (String id : nodes.keySet()) downstream.put(id, new ArrayList<>());
        Set<Edge> unique = new HashSet<>();
        for (Edge edge : connections) {
            if (edge == null || !nodes.containsKey(edge.from()) || !nodes.containsKey(edge.to()))
                throw new IllegalArgumentException("连接引用了不存在的水库");
            if (!unique.add(edge)) throw new IllegalArgumentException("重复连接会重复计算来水");
            downstream.get(edge.from()).add(edge.to());
            if (downstream.get(edge.from()).size() > 1)
                throw new IllegalArgumentException("本教学模型不支持分流；不能把全部出库水复制给多个下游");
            upstream.get(edge.to()).add(edge.from());
        }
        // Kahn 拓扑排序：只有直接上游都已计算的库才能入队。
        Map<String, Integer> degree = new HashMap<>();
        Deque<String> ready = new ArrayDeque<>();
        for (String id : nodes.keySet()) {
            degree.put(id, upstream.get(id).size());
            if (degree.get(id) == 0) ready.add(id);
        }
        List<String> sorted = new ArrayList<>();
        while (!ready.isEmpty()) {
            String id = ready.remove();
            sorted.add(id);
            for (String next : downstream.get(id)) {
                degree.put(next, degree.get(next) - 1);
                if (degree.get(next) == 0) ready.add(next);
            }
        }
        if (sorted.size() != nodes.size()) throw new IllegalArgumentException("水系存在环，不能进行拓扑汇流计算");
        upstream.replaceAll((id, list) -> List.copyOf(list));
        this.order = List.copyOf(sorted);
        this.edges = List.copyOf(connections);
    }

    public Node node(String id) {
        Node node = nodes.get(id);
        if (node == null) throw new IllegalArgumentException("未知水库：" + id);
        return node;
    }
    public Set<String> ids() { return Collections.unmodifiableSet(nodes.keySet()); }
    public List<String> upstreamOf(String id) { node(id); return upstream.get(id); }
    public List<String> topologicalOrder() { return order; }
    public List<Edge> edges() { return edges; }
}
