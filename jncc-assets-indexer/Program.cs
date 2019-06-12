using System;
using System.IO;
using System.Threading.Tasks;
using Amazon;
using Amazon.S3;
using Amazon.SQS;
using Amazon.SQS.Model;
using Amazon.Runtime;
using Amazon.SQS.ExtendedClient;
using Newtonsoft.Json;
using dotenv.net;
using System.Linq;
using System.Collections.Generic;
using System.Net;
using CsvHelper;
using System.Threading;

namespace assetIndexer
{
    class Program
    {
        public static void Main(string[] args)
        {
            // Read in command line parameters

            var assetListUrls = GetUrls();

            ProcessAssetLists(assetListUrls);
        }

        static void ProcessAssetLists(IEnumerable<string> assetListUrls)
        {
            Console.WriteLine("sqs endpoint: {0}", Env.Var.SqsEndpoint);

            using(var s3 = GetS3Client())
            using(var sqs = GetSQSClient())
            using(var sqsExtendedClient = new AmazonSQSExtendedClient(sqs,
                new ExtendedClientConfiguration().WithLargePayloadSupportEnabled(s3, Env.Var.SqsPayloadBucket)
            ))
            {
                foreach (var assetListUrl in assetListUrls)
                {
                    Console.WriteLine("Processing {0}", assetListUrl);

                    List<Asset> assets;

                    try
                    {
                        assets = GetAssetList(assetListUrl);
                    }
                    catch (Exception e)
                    {
                        Console.WriteLine("Unable to get asset list from {0}. Error: {1}", assetListUrl, e.Message);
                        continue;
                    }
              
                    for (int i = 0; i < assets.Count; i++)
                    {
                        var asset = assets[i];
                        
                        Console.WriteLine("Processing Asset {0}, {1} {2}", i, asset.Id, asset.Title);

                        if (AssetValidator.IsValid(asset))
                        {
                            var assetFileUrl = GetFileUrl(assetListUrl, asset.FileName);
                            
                            AssetFile file; 
                            try
                            {
                                file = GetAssetFile(assetFileUrl, asset);
                            }
                            catch (Exception e)
                            {
                                Console.WriteLine("Unable to get asset file from {0}. Error: {1}", assetFileUrl, e.Message);
                                continue;
                            }
                            
                            var message = new
                            {
                                verb = "upsert",
                                index = Env.Var.EsIndex,
                                document = new
                                {
                                    id = asset.Id,
                                    site = Env.Var.EsSite,
                                    title = asset.Title,
                                    url = file.Url,
                                    published_date = asset.PublicationDate,
                                    file_base64 = file.EncodedFile, // base-64 encoded file
                                    file_extension = file.Extension,   // when this is a downloadable
                                    file_bytes = file.Bytes.ToString(),   // file such as a PDF, etc.
                                }
                            };

                            
                            var response = sqsExtendedClient.SendMessageAsync(Env.Var.SqsEndpoint,
                                JsonConvert.SerializeObject(message, Formatting.None)
                            ).GetAwaiter().GetResult();

                            Console.WriteLine("Created Message Id {0}", response.MessageId);

                            if (Env.Var.AssetQueryDelay > 0)
                            {
                                Console.WriteLine("Waiting {0}ms before getting next asset");
                                Thread.Sleep(Env.Var.AssetQueryDelay);
                            }
                        }
                    }
                };
            }
        }

        static AmazonSQSClient GetSQSClient()
        {
            if (Env.Var.UseLocalstack)
            {
                return new AmazonSQSClient(new AmazonSQSConfig {
                    ServiceURL = "http://localhost:4576",
                });
            }
            else
            {
                return new AmazonSQSClient(
                    new BasicAWSCredentials(Env.Var.AwsAccessKeyId, Env.Var.AwsSecretAccessKey), 
                    RegionEndpoint.GetBySystemName(Env.Var.AwsRegion));
            }
        }

        static AmazonS3Client GetS3Client()
        {
            if (Env.Var.UseLocalstack) 
            {
                return new AmazonS3Client(new AmazonS3Config {
                    ServiceURL = "http://localhost:4572",
                    ForcePathStyle = true,
                });
            }
            else 
            {
                return new AmazonS3Client(
                    new BasicAWSCredentials(Env.Var.AwsAccessKeyId, Env.Var.AwsSecretAccessKey), 
                    RegionEndpoint.GetBySystemName(Env.Var.AwsRegion));
            }
        }

        static List<Asset> GetAssetList(string assetListUrl)
        {
            var assets = new List<Asset>();

            using (var stream = new WebClient().OpenRead(assetListUrl))
            using (var reader = new StreamReader(stream))
            using (var csv = new CsvReader(reader))
            {
                assets.AddRange(csv.GetRecords<Asset>());
            }

            return assets;
        }

        static AssetFile GetAssetFile(string assetFileUrl, Asset asset)
        {
            var assetFile = new AssetFile();

            using (var stream = new WebClient().OpenRead(assetFileUrl))
            using (var memstream = new MemoryStream())
            {
                stream.CopyTo(memstream);
                assetFile.Extension = Path.GetExtension(asset.FileName);
                assetFile.Bytes = memstream.Length;
                assetFile.EncodedFile = Convert.ToBase64String(memstream.ToArray());
                assetFile.Url = assetFileUrl;
            }

            return assetFile;
        }

        static string GetFileUrl(string url, string fileName)
        {
            var uri = new Uri(url);
            return uri.AbsoluteUri.Remove(uri.AbsoluteUri.Length - uri.Segments.Last().Length) + fileName;
        }

        static List<string> GetUrls()
        {
            using (StreamReader fileReader = new StreamReader("configuration.json"))
            {
                return JsonConvert.DeserializeObject<List<string>>(fileReader.ReadToEnd());
            }
        }
    }
}