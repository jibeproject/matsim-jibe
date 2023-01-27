package accessibility;

import org.apache.log4j.Logger;
import org.matsim.api.core.v01.Id;
import org.matsim.api.core.v01.IdMap;
import org.matsim.api.core.v01.network.Node;
import org.matsim.core.utils.io.IOUtils;
import org.matsim.core.utils.misc.StringUtils;

import java.io.BufferedReader;
import java.io.IOException;

public class NodeReader {

        private final static Logger log = Logger.getLogger(NodeReader.class);
        private final static char SEP = ',';

        public static IdMap<Node, Double> read(String filename) throws IOException {

                int lines = 0;
                IdMap<Node, Double> nodeAccessibilities = new IdMap<>(Node.class);
                String expectedHeader = "NODE" + SEP + "ACCESSIBILITY" + SEP + "NORMALISED";

                BufferedReader reader = IOUtils.getBufferedReader(filename);

                String header = reader.readLine();
                if (!expectedHeader.equals(header)) {
                        throw new RuntimeException("Bad header, expected '" + expectedHeader + "', got: '" + header + "'.");
                }
                String line;
                while ((line = reader.readLine()) != null) {
                        lines++;
                        String[] parts = StringUtils.explode(line, SEP);
                        Id<Node> id = Id.createNodeId(Integer.parseInt(parts[0]));
                        double accessibility = Double.parseDouble(parts[1]);
                        nodeAccessibilities.put(id, accessibility);
                }
                log.info("Read " + lines + " lines.");
                return nodeAccessibilities;
        }
}

