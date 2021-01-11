package it.unibo.arces.wot.sepa.engine.processing.epspec;

import java.io.IOException;
import java.util.ArrayList;

import it.unibo.arces.wot.sepa.commons.exceptions.SEPABindingsException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASecurityException;
import it.unibo.arces.wot.sepa.commons.exceptions.SEPASparqlParsingException;
import it.unibo.arces.wot.sepa.engine.processing.ARQuadsAlgorithm;
import it.unibo.arces.wot.sepa.engine.processing.updateprocessing.AsksAsSelectExistsList;
import it.unibo.arces.wot.sepa.engine.processing.updateprocessing.IAsk;
import it.unibo.arces.wot.sepa.engine.processing.updateprocessing.UpdateExtractedData;
import it.unibo.arces.wot.sepa.engine.scheduling.InternalUpdateRequest;

public class BlazegraphSpecification implements IEndPointSpecification {

	public boolean asksAsSelectExistListCompare(String value) {
		return value.compareTo("true")==0;
	}
	
	public String s() {
		// TODO Auto-generated method stub
		return "subject";
	}

	public String p() {
		// TODO Auto-generated method stub
		return "predicate";
	}

	public String o() {
		// TODO Auto-generated method stub
		return "object";
	}

	public String g() {
		// TODO Auto-generated method stub
		return "g";
	}
	
	public ArrayList<String> vars() {
		ArrayList<String> vars = new ArrayList<String>();
		vars.add(s());
		vars.add(p());
		vars.add(o());
		return vars;
	}

	@Override
	public IAsk getAsk(ArrayList<UpdateExtractedData> ueds, InternalUpdateRequest req, ARQuadsAlgorithm algorithm){
		return new AsksAsSelectExistsList(ueds, req, algorithm);
	}

}