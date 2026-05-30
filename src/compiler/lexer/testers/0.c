
int sum()
{
	int	 i;
	int v[5];
	int s;
	s=0;
	for(i=0;i<5;i=i+1){
		v[i]=i;
		s=s+v[i];
		}
	return s;
}

void put_i(int x){
	
}

void main()
{
	int		i;
	int s;
	for(i=0;i<1000000;i=i+1)
	s=sum();
	put_i(s);
}

