namespace my.bookshop;

using { cuid, managed } from '@sap/cds/common';

entity AiUsageRecords : cuid, managed {
	model        : String(200);
	inputTokens  : Integer;
	outputTokens : Integer;
	totalTokens  : Integer;
}
