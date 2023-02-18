# Transport time
Adding transports (e.g. teleports, boats, agility shortcuts, ...) into the pathfinding is quite straight forward, but if the transport involves travel waiting time (e.g. cutscene, stall, ...) it gets more complicated (see e.g. [issue #23](https://github.com/Skretzo/shortest-path/issues/23)).

Solution:  
**Cost:** a metric that combines distance travelled and travel waiting time.

I think there are two options:
- **Dijkstra**:  
Automatically sort nodes to be explored based on cost.
- **Breadth-first search (BFS)**:  
Manually insert nodes with travel waiting time into the nodes to be explored based on cost.

At a glance the Dijkstra approach looks the most promising, but it comes with a few drawbacks that are not negligible for large datasets:
- Need to sort the nodes to be explored on every iteration (e.g. [`PriorityQueue`](https://docs.oracle.com/javase/8/docs/api/java/util/PriorityQueue.html))
- Need to store visited nodes to later update the calculating path (e.g. [`HashMap`](https://docs.oracle.com/javase/8/docs/api/java/util/HashMap.html))

https://github.com/Skretzo/shortest-path/blob/8ee6f6e0ca6e419c9c319dd939dd03799a7f1330/src/main/java/shortestpath/pathfinder/Pathfinder.java#L43-L55

---

The BFS approach also involves some form of sorting, namely the nodes with travel waiting time (stored in a pending priority queue)

https://github.com/Skretzo/shortest-path/blob/a69e81282fdaf109855e5d75a4c82d6a6139fec2/src/main/java/shortestpath/pathfinder/Pathfinder.java#L64-L72

As far as I can tell, both algorithm options produce paths identical to eachother (and accurate to OSRS).

- Are there any better options?
- Are there any potential future problems with using either of the two options?

---

The difference between the two options can be seen in full detail in code here:  
https://github.com/Skretzo/shortest-path/commit/a69e81282fdaf109855e5d75a4c82d6a6139fec2?diff=split#diff-52a8d2893d794d77866281270ec3535651946019cffe758ec44c5b37df1a3234

---

Some empirical testing indicates that the BFS approach is faster than Dijkstra:
- Calculating the path from Corsair Cove (2488, 2860, 0) to Demonic Ruins (3333, 3892, 0) with agility shortcuts and without boats takes roughly 2.5 seconds for BFS and 3.0 seconds for Dijkstra. With boats included this changes to 4.4 seconds and 5.5 seconds, respectively.
