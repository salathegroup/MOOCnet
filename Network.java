package MOOCnet;
/**
 * Created with IntelliJ IDEA.
 * User: ellscampbell
 * Date: 5/7/13
 * Time: 12:22 PM
 */

import edu.uci.ics.jung.algorithms.cluster.WeakComponentClusterer;
import edu.uci.ics.jung.graph.Graph;
import edu.uci.ics.jung.graph.SparseGraph;
import edu.uci.ics.jung.graph.util.EdgeType;
import edu.uci.ics.jung.graph.util.Pair;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.*;

public class Network {

    private Random            random = new Random()  ;
    private Graph<Node, Edge> graph                  ;
    private Node[]            nodes                  ;
    private Edge[]            edges                  ;
    private int               restartCounter = 0     ;
    private boolean           g2g            = false ;
    private int               refusalCount   = 0     ;
    private double            finalR         = 0     ;
    private double            finalCV        = 0     ;
    private double            deltaR         = 0     ;
    private int               controlCounter = 0     ;



    public void initSmallWorldGraph() {
        double rewire        = Settings.getInstance().getSmallWorldRewireProbability() ;
        int    numberOfNodes = Settings.getInstance().getNumberOfNodes()               ;
        int    meanDegree    = Settings.getInstance().getMeanDegree()                  ;
        int    maxDegree     = Settings.getInstance().getMaximumDegree()               ;
        int    minDegree     = Settings.getInstance().getMinimumDegree()               ;
               this.nodes    = new Node[numberOfNodes]                                 ;
        Set    components                                                              ;
        do {
            this.graph = new SparseGraph<Node, Edge>();
            for (int i = 0; i < numberOfNodes; i++) {
                Node node = new Node(i);
                this.nodes[i] = node;
                this.graph.addVertex(node);
            }
            for (int i = 0; i < numberOfNodes; i++) {
                for (int ii = 0; ii < meanDegree; ii++) {
                    int diff = ii/2 + 1;
                    if (ii%2 == 1) diff *= -1;
                    int newIndex = i + diff;
                    if (newIndex < 0) newIndex += numberOfNodes;
                    if (newIndex >= numberOfNodes) newIndex -= numberOfNodes;
                    Edge newEdge = new Edge();
                    this.graph.addEdge(newEdge, this.nodes[i], this.nodes[newIndex], EdgeType.UNDIRECTED);
                }
            }
            for (Edge edge:this.graph.getEdges()) {
                if (this.random.nextDouble() < rewire) {
                    Node source = this.graph.getEndpoints(edge).getFirst();

                    // if source has minimum degree, try the source of the next edge
                    if (this.graph.degree(source) == minDegree) continue;

                    Node newDestination;

                    do {
                        newDestination = this.nodes[this.random.nextInt(numberOfNodes)];
                    }
                    // select a new random destination if nodes are already neighbors OR if it would generate a self-loop OR if its degree would exceed the maximum degree
                    while ((this.graph.isNeighbor(source,newDestination)) || (source.equals(newDestination)) || (this.graph.degree(newDestination) == maxDegree));

                    this.graph.removeEdge(edge);
                    Edge newEdge = new Edge();
                    this.graph.addEdge(newEdge, source, newDestination, EdgeType.UNDIRECTED);
                }
            }
            WeakComponentClusterer wcc = new WeakComponentClusterer();
            components = wcc.transform(this.graph);
        }
        while (components.size() > 1);
        this.remakeEdgeList();
    }

    public void initRandomGraph() {
        Set components                                                ;
        int numberOfNodes = Settings.getInstance().getNumberOfNodes() ;
        int meanDegree    = Settings.getInstance().getMeanDegree()    ;
        this.nodes        = new Node[numberOfNodes]                   ;
        do {
            this.graph = new SparseGraph<Node, Edge>();
            for (int i = 0; i < numberOfNodes; i++) {
                Node nodeToAdd = new Node(i)      ;
                this.nodes[i]  = nodeToAdd        ;
                this.graph.addVertex(nodeToAdd)   ;
            }

            for (Node source : this.graph.getVertices()) {
                while (this.graph.degree(source) < meanDegree) {
                    this.restartCounter++;
                    if (this.restartCounter > 5000) {
                        this.restartCounter = 0;
                        return;
                    }
                    Node newDestination;
                    do {
                        newDestination = this.nodes[this.random.nextInt(this.nodes.length)];
                    }
                    // select a new random destination if it would generate a self-loop OR if nodes are already neighbors
                    while((newDestination == source) || (this.graph.isNeighbor(source,newDestination)));

                    Edge newEdge = new Edge();
                    this.graph.addEdge(newEdge, source, newDestination);

                }
            }
            WeakComponentClusterer wcc = new WeakComponentClusterer();
            components = wcc.transform(this.graph);
        }
        while ((components.size() > 1) || (this.getMeanDegree() < 10)) ;
        this.g2g = true               ;
        this.remakeEdgeList()         ;
    }

    public void runSmallWorld() {
        Set components;
        double targetCV = Settings.getInstance().getTargetCV();
        this.initSmallWorldGraph();
        int counter = 0;
        double cv = this.getCV();

        // ignore minimum and maximum degree boundaries if generating high degree variability network (large cv)
        if (this.getCV() > 0) {
            Settings.getInstance().setMaximumDegree(Integer.MAX_VALUE);
            Settings.getInstance().setMinimumDegree(1);
        }

        while (cv < targetCV) {
            this.rewire();

            //slow, but useful for testing & monitoring
            if (counter%1000 == 0){
                WeakComponentClusterer wcc = new WeakComponentClusterer();
                components = wcc.transform(this.graph);
                boolean connected = true;
                boolean staticMeanDegree = false;
                if (components.size() == 1) connected = true;
                double meanDegree = this.getMeanDegree();
                if (meanDegree == Settings.getInstance().getMeanDegree()) staticMeanDegree = true;
                System.out.println(String.format("%.2f",(100*(cv/targetCV))/2.0) + "% Complete\t||\tCV: " + cv + "\t||\tFully Connected?: " + connected + " \t||\tMean Degree Uniformity?: " + staticMeanDegree + "\t||\tCalculated Arithmetic Mean Degree: " + meanDegree);
            }

            counter++;
            cv = this.getCV();
        }
        this.finalCV = cv;

    }

    public void runRandom() {
        System.out.println("THIS MAY TAKE A FEW ATTEMPTS...please hold...");
        Set components;
        do {
            this.initRandomGraph();
        }
        while (!this.g2g);

        double targetCV = Settings.getInstance().getTargetCV();
        int counter = 0;
        double cv = this.getCV();

        // ignore minimum and maximum degree boundaries if generating high degree variability network (large cv)
        if (this.getCV() > 0) {
            Settings.getInstance().setMaximumDegree(Integer.MAX_VALUE);
            Settings.getInstance().setMinimumDegree(1);
        }

        while (cv < targetCV) {
            this.rewire();

            //slow, but useful for testing & monitoring
            if (counter%1000 == 0) {
                WeakComponentClusterer wcc = new WeakComponentClusterer();
                components = wcc.transform(this.graph);
                boolean connected = true;
                boolean staticMeanDegree = false;
                if (components.size() == 1) connected = true;
                if (this.getMeanDegree() == Settings.getInstance().getMeanDegree()) staticMeanDegree = true;
                System.out.println(String.format("%.2f",(100*(cv/targetCV))/2.0) + "% Complete\t||\tCV: " + cv + "\t||\tFully Connected?: " + connected + "\t||\tDegree Uniformity?: " + staticMeanDegree + "\t" + this.getMeanDegree());
            }
            cv = this.getCV();
            counter++;
        }
        this.finalCV = cv;
    }

    private void rewire() {
        double currentCV = this.getCV();
        int minDegree = Settings.getInstance().getMinimumDegree();
        int maxDegree = Settings.getInstance().getMaximumDegree();

        Edge edge = this.edges[this.random.nextInt(this.edges.length)];

        if (this.graph.getEndpoints(edge) == null) return;

        Node source = this.graph.getEndpoints(edge).getFirst();
        Node destination = this.graph.getEndpoints(edge).getSecond();

        // if either source or destination is already at min degree limit, return
        if ((this.graph.degree(source) == minDegree) || (this.graph.degree(destination) == minDegree)) return;

        // pick a new random destination IF:
        // 1.) the chosen destination IS the source...OR...
        // 2.) is already connected to the source...OR...
        // 3.) is already at the maximum allowable degree
        Node newDestination;
        do{
            newDestination = this.nodes[this.random.nextInt(this.nodes.length)];
        }
        while ((this.graph.isNeighbor(source,newDestination)) || (newDestination == source) || (this.graph.degree(newDestination) == maxDegree)) ;

        // self loof check should be redundant, but better safe than sorry

        //check for creating a double edge
        if (this.graph.getNeighbors(source).contains(newDestination)) return;
        //check for a self edge
        if (source.getID() == newDestination.getID()) return;

        //remove edge HERE (after we've confirmed that we're not about to make a self-edge or double-edge
        this.graph.removeEdge(edge);

        //draw the new edge
        Edge newEdge = new Edge();
        this.graph.addEdge(newEdge, source, newDestination);

        Set components;
        WeakComponentClusterer wcc = new WeakComponentClusterer();
        components = wcc.transform(this.graph);

        // rewires that decrease CV or disconnect the GCC are reversed
        if (this.getCV() < currentCV || components.size()>1) {
            this.graph.removeEdge(newEdge);
            this.graph.addEdge(new Edge(), source, destination);
        }
        //re-make this.edges to account for the changes and move to the next
        this.remakeEdgeList();
    }

    private void printEdgeList(String filename) {
        System.out.println("printing edgeList to file: " + filename);

        PrintWriter out = null;
        try {
            out = new PrintWriter(new java.io.FileWriter(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
        out.println("source\ttarget");
        for (Edge edge : this.graph.getEdges()) {
            out.println(this.graph.getEndpoints(edge).getFirst().getID() + "\t" + this.graph.getEndpoints(edge).getSecond().getID());
        }
        out.close();
    }

    private void printNodeList(String filename) {
        System.out.println("printing nodeList to file: " + filename);

        PrintWriter out = null;
        try {
            out = new PrintWriter(new java.io.FileWriter(filename));
        } catch (IOException e) {
            e.printStackTrace();
        }
        out.println("id\tstatus");
        for (Node node : this.graph.getVertices()) {
            out.println(node.getID() + "\t" + node.getStatus());

        }
        out.close();
    }

    public void assignVaccinationSentimentStatus() {
        int    numberOfNodes    = Settings.getInstance().getNumberOfNodes();
        double refusalCovg      = Settings.getInstance().getRefusalCoverage();
        int    numberOfRefusers = (int)Math.round(numberOfNodes * refusalCovg);
        this.refusalCount=0;

        while (this.refusalCount < numberOfRefusers) {
            Node randomNode;
            do {
                randomNode = this.nodes[this.random.nextInt(numberOfNodes)];
            }
            while (randomNode.getStatus() != Node.NEUTRAL);
            if (this.random.nextDouble() < refusalCovg) {
                randomNode.setStatus(Node.REFUSE);
                this.refusalCount++;
            }
        }
        for (Node node : this.graph.getVertices()) {
            if (node.getStatus() == Node.NEUTRAL) node.setStatus(Node.ACCEPT);
        }
    }

    public void resetSentiment() {
        for (Node node : this.graph.getVertices()) {
            node.setStatus(Node.NEUTRAL);
        }
        this.refusalCount=0;
    }

    public double increaseAssortativity() {
        double currentR  = this.measureAssortativity();
        double targetR   = Settings.getInstance().getAssortativityTarget();
        Set components;
        int printCounter = 0;
        if (currentR < targetR) {
            while (currentR < targetR) {
                if (this.controlCounter > 50000) {
                    System.out.println("reset");
                    this.resetSentiment();
                    this.assignVaccinationSentimentStatus();
                    currentR = this.measureAssortativity();
                    this.controlCounter=0;
                    int refusal = 0;
                    for (Node node : this.graph.getVertices()) {
                        if (node.getStatus() == Node.REFUSE) refusal++;
                    }
                }
                currentR = this.assortVaccineRefusal(currentR);
                printCounter++;
                if (printCounter%1000==0) {
                    WeakComponentClusterer wcc = new WeakComponentClusterer();
                    components = wcc.transform(this.graph);
                    boolean connected = true;
                    boolean staticMeanDegree = false;
                    if (components.size() == 1) connected = true;
                    if (this.getMeanDegree() == Settings.getInstance().getMeanDegree()) staticMeanDegree = true;
                    System.out.println(String.format("%.2f", 50+(100*(currentR/targetR)/2.0)) + "% Complete\t||\tr: " + currentR + "\t||\tLastDelta: " + String.format("%.9f",this.deltaR) + "\t||\tFailedSwaps: " + this.controlCounter);
                }
            }
        }
        this.finalR = this.measureAssortativity();
        return this.finalR;
    }
    private double measureAssortativity() {
        /**
         * This method counts each type of edge in the network, and returns those values to calculate assortativity
         */
        int numberOfEdges_Neg_Neg = 0;
        int numberOfEdges_Pos_Pos = 0;
        int numberOfEdges_Pos_Neg = 0;
        int numberOfEdges_Neg_Pos = 0;

        for (Edge edge : this.graph.getEdges()) {
            Pair pair        =  this.graph.getEndpoints(edge) ;
            Node source      =  (Node)pair.getFirst()   ;
            Node destination =  (Node)pair.getSecond()  ;
            if (source.getStatus() == Node.REFUSE && destination.getStatus() == Node.REFUSE) numberOfEdges_Neg_Neg++ ;
            if (source.getStatus() == Node.ACCEPT && destination.getStatus() == Node.ACCEPT) numberOfEdges_Pos_Pos++ ;
            if (source.getStatus() == Node.ACCEPT && destination.getStatus() == Node.REFUSE) numberOfEdges_Pos_Neg++ ;
            if (source.getStatus() == Node.REFUSE && destination.getStatus() == Node.ACCEPT) numberOfEdges_Neg_Pos++ ;
        }

        return this.getAssortativity(numberOfEdges_Neg_Neg, numberOfEdges_Pos_Pos, numberOfEdges_Pos_Neg, numberOfEdges_Neg_Pos);
    }

    public double getAssortativity(int numberOfEdges_Neg_Neg, int numberOfEdges_Pos_Pos, int numberOfEdges_Pos_Neg, int numberOfEdges_Neg_Pos) {
        int    numberOfEdges     = numberOfEdges_Neg_Neg + numberOfEdges_Pos_Pos + numberOfEdges_Pos_Neg + numberOfEdges_Neg_Pos ;
        double eII_neg           = (double)numberOfEdges_Neg_Neg / numberOfEdges ;
        double eII_pos           = (double)numberOfEdges_Pos_Pos / numberOfEdges ;
        double eIJ_pos_neg       = (double)numberOfEdges_Pos_Neg / numberOfEdges ;
        double eIJ_neg_pos       = (double)numberOfEdges_Neg_Pos / numberOfEdges ;

        double a_pos = (eII_pos + eIJ_neg_pos);
        double a_neg = (eII_neg + eIJ_pos_neg);

        double r = ((eII_neg + eII_pos) - (a_pos * a_neg)) / (1 - (a_pos * a_neg)) ;

        return r;
    }

    private double assortVaccineRefusal(double currentR) {
        /**
         * This method will either increase or decrease assortative mixing, depending on the target assortativity
         * This method will maintain the underlying topology of the network, only modifying node characteristics
         * For each change, assortativity is re-calculated and compared to the target assortativity.
         * If the change is undesired, node characteristics are reverted.
         * Successful or unsuccessful, the method returns the current assortativity (r) of the network.
         */

        int randomIndex ;

        // generate node & edge arrays for index access
        ArrayList<Edge>     edges = new ArrayList<Edge>() ;
        ArrayList<Node>     nodes = new ArrayList<Node>() ;
        for (Node node : this.graph.getVertices()) {
            nodes.add(node);
        }
        for (Edge edge : this.graph.getEdges()) {
            edges.add(edge);
        }

        // initialize node involved
        Node p1, p2, pSwap;

        // pick a random edge that connects nodes of either of the two offset-types  (accept->refuse or refuse->accept)
        do {
            randomIndex = this.random.nextInt(this.graph.getEdgeCount());
            Edge randomEdge = edges.get(randomIndex);
            Pair pair = this.graph.getEndpoints(randomEdge);
            p1 = (Node)pair.getFirst();
            p2 = (Node)pair.getSecond();
        }
        while(p1.getStatus() == Node.ACCEPT && p2.getStatus() == Node.ACCEPT ||
                p1.getStatus() == Node.REFUSE && p2.getStatus() == Node.REFUSE);

        // first, we'll try swapping out p1's sentiment
        pSwap = p1;

        // we only swap refusal sentiments, acceptors remain unchanged
        if (p1.getStatus() == Node.ACCEPT) pSwap = p2;

        // swap first node now! otherwise calculations will be wrong if second node
        pSwap.setStatus(Node.ACCEPT);

        // now pick a random node that is accept, and that is not connected to a accept node
        Node pRandom;

        // first make sure the node is accept
        do {
            randomIndex = this.random.nextInt(this.graph.getVertexCount());
            pRandom = (Node) nodes.get(randomIndex);
        }
        while (pRandom.getStatus() == Node.REFUSE);

        pRandom.setStatus(Node.REFUSE);
        double r = this.measureAssortativity();
        this.controlCounter++;
        if (r - currentR < 0) {
            pSwap.setStatus(Node.REFUSE);
            pRandom.setStatus(Node.ACCEPT);
            return currentR;
        }
        else {
            this.controlCounter=0;
            if (this.deltaR != Math.abs(r-currentR) && Math.abs(r-currentR) != 0 ) this.deltaR = Math.abs(r-currentR);
            return r;
        }
    }
    public double getMeanDegree() {
        int sumDegree = 0;
        for (Node node : this.graph.getVertices()) {
            sumDegree += this.graph.degree(node);
        }
        double meanDegree = (1.0*sumDegree) / this.graph.getVertexCount();
        return meanDegree;
    }

    public double getStandardDeviation() {
        double standardDeviation;
        double meanDegree = this.getMeanDegree();
        double sumDifSquares = 0;
        for (Node node : this.graph.getVertices()) {
            double dif = this.graph.degree(node) - meanDegree;
            double difSquared = dif*dif;
            sumDifSquares += difSquared;
        }
        standardDeviation = Math.pow(sumDifSquares / this.graph.getVertexCount(), 0.5);
        return standardDeviation;
    }

    private double getCV() {
        double stDev = this.getStandardDeviation();
        double meanDeg = this.getMeanDegree();
        return stDev/meanDeg;
    }

    private void remakeEdgeList() {
        int i = 0;
        this.edges = new Edge[this.graph.getEdgeCount()];
        for (Edge edge : this.graph.getEdges()) {
            this.edges[i] = edge;
            i++;
        }
    }

    public void printResultsToFile() {
        String networkType = null;
        this.finalCV = this.getCV();
        this.finalR = this.measureAssortativity();
        if (Settings.getInstance().getNetworkType() == Manager.RANDOM_NET)     networkType = "randomNet"     ;
        if (Settings.getInstance().getNetworkType() == Manager.SMALLWORLD_NET) networkType = "smallWorldNet" ;
        String uniqueTimeStamp = String.format("%7f", ((double)System.currentTimeMillis())/1000);

        this.printEdgeList(networkType + "_edgeList"
                + "_assort_"    + String.format("%.6f", this.finalR)
                + "_CV_"        + String.format("%.2f", this.finalCV)
                + "_k_"         + this.getMeanDegree()
                + "_refusal_"   + String.format("%.2f", (1.0*this.refusalCount/this.graph.getVertexCount()))
                + "_nodeCount_" + this.graph.getVertexCount()
                + "_ts_"    + uniqueTimeStamp);

        this.printNodeList(networkType + "_nodeList"
                + "_assort_"    + String.format("%.6f", this.finalR)
                + "_CV_"        + String.format("%.2f", this.finalCV)
                + "_k_"         + this.getMeanDegree()
                + "_refusal_"   + String.format("%.2f", (1.0*this.refusalCount/this.graph.getVertexCount()))
                + "_nodeCount_" + this.graph.getVertexCount()
                + "_ts_"        + uniqueTimeStamp);
    }

    public Graph<Node,Edge> returnGraph() {
        return this.graph;
    }

}
