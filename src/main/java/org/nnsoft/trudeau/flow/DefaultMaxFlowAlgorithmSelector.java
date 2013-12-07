package org.nnsoft.trudeau.flow;

/*
 *   Copyright 2013 The Trudeau Project
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 */

import static org.nnsoft.trudeau.utils.Assertions.checkNotNull;
import static org.nnsoft.trudeau.visit.GraphVisitor.visit;

import org.nnsoft.trudeau.api.DirectedGraph;
import org.nnsoft.trudeau.api.Mapper;
import org.nnsoft.trudeau.api.VertexPair;
import org.nnsoft.trudeau.inmemory.DirectedMutableGraph;
import org.nnsoft.trudeau.math.monoid.OrderedMonoid;

/**
 * {@link MaxFlowAlgorithmSelector} implementation.
 *
 * @param <V> the Graph vertices type
 * @param <WE> the Graph edges type
 * @param <W> the Graph weight type
 */
final class DefaultMaxFlowAlgorithmSelector<V, WE, W>
    implements MaxFlowAlgorithmSelector<V, WE, W>
{

    private final DirectedGraph<V, WE> graph;

    private final Mapper<WE, W> weightedEdges;

    private final V source;

    private final V target;

    public DefaultMaxFlowAlgorithmSelector( DirectedGraph<V, WE> graph, Mapper<WE, W> weightedEdges, V source, V target )
    {
        this.graph = graph;
        this.weightedEdges = weightedEdges;
        this.source = source;
        this.target = target;
    }

    /**
     * {@inheritDoc}
     */
    public <WO extends OrderedMonoid<W>> W applyingFordFulkerson( WO weightOperations )
    {
        final WO checkedWeightOperations = checkNotNull( weightOperations, "Weight operations can not be null to find the max flow in the graph" );

        // create flow network
        final DirectedGraph<V, EdgeWrapper<WE>> flowNetwork = newFlowNetwok( graph, checkedWeightOperations );

        // create flow network handler
        final FlowNetworkHandler<V, EdgeWrapper<WE>, W> flowNetworkHandler =
                        new FlowNetworkHandler<V, EdgeWrapper<WE>, W>( flowNetwork, source, target, checkedWeightOperations, new MapperWrapper<WE, W, WO>( checkedWeightOperations, weightedEdges ) );

        // perform depth first search
        visit( flowNetwork ).from( source ).applyingDepthFirstSearch( flowNetworkHandler );

        while ( flowNetworkHandler.hasAugmentingPath() )
        {
            // update flow network
            flowNetworkHandler.updateResidualNetworkWithCurrentAugmentingPath();
            // look for another augmenting path with depth first search
            visit( flowNetwork ).from( source ).applyingDepthFirstSearch( flowNetworkHandler );
        }

        return flowNetworkHandler.onCompleted();
    }

    /**
     * {@inheritDoc}
     */
    public <WO extends OrderedMonoid<W>> W applyingEdmondsKarp( WO weightOperations )
    {
        final WO checkedWeightOperations = checkNotNull( weightOperations, "Weight operations can not be null to find the max flow in the graph" );

        // create flow network
        final DirectedGraph<V, EdgeWrapper<WE>> flowNetwork = newFlowNetwok( graph, checkedWeightOperations );

        // create flow network handler
        final FlowNetworkHandler<V, EdgeWrapper<WE>, W> flowNetworkHandler =
                        new FlowNetworkHandler<V, EdgeWrapper<WE>, W>( flowNetwork, source, target, checkedWeightOperations, new MapperWrapper<WE, W, WO>( checkedWeightOperations, weightedEdges ) );

        // perform breadth first search
        visit( flowNetwork ).from( source ).applyingBreadthFirstSearch( flowNetworkHandler );

        while ( flowNetworkHandler.hasAugmentingPath() )
        {
            // update flow network
            flowNetworkHandler.updateResidualNetworkWithCurrentAugmentingPath();
            // look for another augmenting path with breadth first search
            visit( flowNetwork ).from( source ).applyingBreadthFirstSearch( flowNetworkHandler );
        }

        return flowNetworkHandler.onCompleted();
    }

    private <WO extends OrderedMonoid<W>> DirectedGraph<V, EdgeWrapper<WE>> newFlowNetwok( final DirectedGraph<V, WE> graph,
                                                                                           final WO weightOperations )
    {
        DirectedMutableGraph<V, EdgeWrapper<WE>> flowGraph = new DirectedMutableGraph<V, EdgeWrapper<WE>>();

        // vertices
        for ( V vertex : graph.getVertices() )
        {
            flowGraph.addVertex( vertex );
        }

        // edges
        for ( WE edge : graph.getEdges() )
        {
            VertexPair<V> edgeVertices = graph.getVertices( edge );
            V head = edgeVertices.getHead();
            V tail = edgeVertices.getTail();

            flowGraph.addEdge( head, new EdgeWrapper<WE>( edge ), tail );

            if ( graph.getEdge( tail, head ) == null )
            {
                // complete the flow network with a zero-capacity inverse edge
                flowGraph.addEdge( tail, new EdgeWrapper<WE>(), head );
            }
        }

        return flowGraph;
    }

    private static final class EdgeWrapper<WE>
    {

        private final WE wrapped;

        public EdgeWrapper()
        {
            this( null );
        }

        public EdgeWrapper( WE wrapped )
        {
            this.wrapped = wrapped;
        }

        public WE getWrapped()
        {
            return wrapped;
        }

    }

    @SuppressWarnings( "serial" ) //the algorithm isn't serializable.
    private static final class MapperWrapper<WE, W, WO extends OrderedMonoid<W>>
        implements Mapper<EdgeWrapper<WE>, W>
    {

        private final WO weightOperations;

        private final Mapper<WE, W> weightedEdges;

        public MapperWrapper( WO weightOperations, Mapper<WE, W> weightedEdges )
        {
            this.weightOperations = weightOperations;
            this.weightedEdges = weightedEdges;
        }

        public W map( EdgeWrapper<WE> input )
        {
            if ( input.getWrapped() == null )
            {
                return weightOperations.identity();
            }
            return weightedEdges.map( input.getWrapped() );
        }

    }

}
