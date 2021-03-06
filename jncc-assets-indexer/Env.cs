using System;
using System.Collections.Generic;
using dotenv.net;
using Newtonsoft.Json;

namespace assetIndexer
{
    public class Env
    {
        static Env() { } // singleton to avoid reading a variable more than once
        private static readonly Env env = new Env();

        public string AwsRegion              { get; private set; }
        public string AwsAccessKeyId         { get; private set; }
        public string AwsSecretAccessKey     { get; private set; }
        public string SqsEndpoint            { get; private set; }
        public string SqsPayloadBucket       { get; private set; }
        public string EsIndex                { get; private set; }
        public string EsSite                 { get; private set; }
        public List<AssetList> AssetLists    { get; private set; }
        public bool UseLocalstack            { get; private set; } = false; 
        public int AssetQueryDelay           { get; private set; } = 0;

        public Env()
        {
            DotEnv.Config();

            AwsRegion = GetVariable("AWS_DEFAULT_REGION");
            AwsAccessKeyId = GetVariable("AWS_ACCESS_KEY_ID");
            AwsSecretAccessKey = GetVariable("AWS_SECRET_ACCESS_KEY");
            // this.HUB_API_ENDPOINT = GetVariable("HUB_API_ENDPOINT");
            SqsEndpoint= GetVariable("SQS_ENDPOINT");
            SqsPayloadBucket = GetVariable("SQS_PAYLOAD_BUCKET"); 
            EsSite = GetVariable("ES_SITE"); 
            EsIndex = GetVariable("ES_INDEX");
            UseLocalstack = Boolean.Parse(GetVariable("USE_LOCALSTACK"));
            AssetQueryDelay = Int32.Parse(GetVariable("ASSET_QUERY_DELAY"));
            AssetLists = JsonConvert.DeserializeObject<List<AssetList>>(GetVariable("ASSET_LIST_URLS"));
        }

        string GetVariable(string variable)
        {
            return Environment.GetEnvironmentVariable(variable)
                ?? throw new Exception($"The environment variable {variable} couldn't be read. You may need to define it in your .env file.");
        }

        public static Env Var
        {
            get { return env; }
        }
    }
}